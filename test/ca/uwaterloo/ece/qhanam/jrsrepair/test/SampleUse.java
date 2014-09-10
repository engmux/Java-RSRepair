package ca.uwaterloo.ece.qhanam.jrsrepair.test;

import java.io.File;

import ca.uwaterloo.ece.qhanam.jrsrepair.*;
import ca.uwaterloo.ece.qhanam.jrsrepair.compiler.JavaJDKCompiler;

public class SampleUse {
	
	public static final String[] SOURCE_DIRECTORY = {"/Users/qhanam/Documents/workspace_faultlocalization/ca.uwaterloo.ece.qhanam.localization/src"};
	public static final String FAULTY_COVERAGE = "/Users/qhanam/Documents/workspace_repair/ca.uwaterloo.ece.qhanam.jrsrepair/cov/faulty.cov";
	public static final String SEED_COVERAGE = "/Users/qhanam/Documents/workspace_repair/ca.uwaterloo.ece.qhanam.jrsrepair/cov/seed.cov";
	
	public static final int MUTATION_CANDIDATES = 40; 
	public static final int MUTATION_GENERATIONS = 10;
	public static final int MUTATION_ATTEMPTS = 100;
	
	public static final String ANT_BASE_DIR = "/Users/qhanam/Documents/workspace_faultlocalization/ca.uwaterloo.ece.qhanam.localization/";
	public static final String ANT_PATH = "/usr/bin/ant";
	public static final String ANT_COMPILE_TARGET = "compile";
	public static final String ANT_TEST_TARGET = "junit";
	
	public static final long RANDOM_SEED = 3;
	
	public static final File PATCH_DIRECTORY = new File("/Users/qhanam/Documents/workspace_faultlocalization/ca.uwaterloo.ece.qhanam.localization/build/patches");
	
	public static final String CLASS_DIRECTORY = "/Users/qhanam/Documents/workspace_faultlocalization/ca.uwaterloo.ece.qhanam.localization/build/classes";
	public static final String CLASSPATH = "/Users/qhanam/Documents/workspace_faultlocalization/ca.uwaterloo.ece.qhanam.localization/build/classes";

	public static void main(String[] args) throws Exception {
		TestExecutor testExecutor = new TestExecutor(new File(ANT_BASE_DIR), ANT_PATH, ANT_COMPILE_TARGET, ANT_TEST_TARGET);
		JavaJDKCompiler compiler = new JavaJDKCompiler(CLASS_DIRECTORY, CLASSPATH);
		JRSRepair repair = new JRSRepair(SOURCE_DIRECTORY, new File(FAULTY_COVERAGE), new File(SEED_COVERAGE), 
										 MUTATION_CANDIDATES, MUTATION_GENERATIONS, MUTATION_ATTEMPTS, RANDOM_SEED, 
										 PATCH_DIRECTORY, compiler, testExecutor);

		repair.buildASTs();
		repair.repair();
	}
}