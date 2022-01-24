package binary_type_inference;

import ghidra.app.plugin.core.osgi.BundleHost;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressFactory;
import ghidra.program.model.data.AbstractIntegerDataType;
import ghidra.program.model.data.Pointer;
import ghidra.program.model.data.Structure;
import ghidra.program.model.lang.LanguageCompilerSpecPair;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.MemoryAccessException;
import ghidra.test.AbstractGhidraHeadlessIntegrationTest;
import ghidra.test.TestEnv;
import ghidra.util.InvalidNameException;
import ghidra.util.exception.CancelledException;
import ghidra.util.exception.DuplicateNameException;
import ghidra.util.exception.VersionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GenerateInputsTest extends AbstractGhidraHeadlessIntegrationTest {
  private static TestEnv env;

  private static Program program;

  private final ClassLoader classLoader = getClass().getClassLoader();

  @Before
  public void setUp() throws IOException {
    env = new TestEnv(this.getClass().getName());
  }

  @After
  public void tearDown() {
    if (program != null)
      env.release(program);
    program = null;
    env.dispose();
  }

  @Test
  public void generateListInputs()
      throws Exception {
    // For future reference, to get processor:
    // Processor.findOrPossiblyCreateProcessor("x86")
    LanguageCompilerSpecPair specPair = new LanguageCompilerSpecPair("x86:LE:32:default", "gcc");
    program = env.getGhidraProject()
        .importProgram(
            new File(
                Objects.requireNonNull(
                    GenerateInputsTest.class
                        .getClassLoader()
                        .getResource("binaries/list_test.o"))
                    .getFile()),
            specPair.getLanguage(),
            specPair.getCompilerSpec());
    env.getGhidraProject().analyze(program, false);

    Set<Function> pres = new HashSet<>();

    var target_func = program.getFunctionManager().getFunctionAt(program.getAddressFactory().getAddress("0x1000"));
    pres.add(target_func);

    PreservedFunctionList pl = new PreservedFunctionList(pres);

    var inf = new BinaryTypeInference(program, pl,
        Arrays.asList("/Users/ian/Code/BTIGhidra/binary_type_inference/cwe_checker/src/ghidra/p_code_extractor"));
    var const_types = inf.produceArtifacts();

    assertTrue("lattice file exists", inf.getLatticeJsonPath().toFile().exists());
    assertTrue("additional constraints file exisits", inf.getAdditionalConstraintsPath().toFile().exists());

    inf.getCtypes();

    assertTrue("Ctypes dont exist", inf.getCtypesOutPath().toFile().exists());

    inf.applyCtype(const_types);

    var hopefully_fixed = program.getFunctionManager().getFunctionAt(program.getAddressFactory().getAddress("0x0000"));
    var target_sig = hopefully_fixed.getSignature();

    var ptr = target_sig.getArguments()[0];

    assertTrue("Arg to linked list func is not pointer", ptr.getDataType() instanceof Pointer);

    var pointed_to = ((Pointer) ptr.getDataType()).getDataType();

    Objects.requireNonNull(pointed_to);
    System.out.println(pointed_to);

    assertTrue("The pointer does not point to a structure", pointed_to instanceof Structure);

    var struct = (Structure) pointed_to;

    assertEquals(8, struct.getLength());

    assertEquals(2, struct.getNumComponents());

    var should_be_self_pointer = struct.getComponent(0).getDataType();

    assertTrue("First field is not pointer", should_be_self_pointer instanceof Pointer);

    var self_pointer = (Pointer) should_be_self_pointer;

    assertEquals(struct, self_pointer.getDataType());

    assertTrue("Second field is not integer", struct.getComponent(1).getDataType() instanceof AbstractIntegerDataType);
  }
}
