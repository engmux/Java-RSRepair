package ca.uwaterloo.ece.qhanam.jrsrepair.test;

import java.io.File;
import ca.uwaterloo.ece.qhanam.jrsrepair.*;

public class SampleUse {
	
	public static final String SOURCE_DIRECTORY = "/Users/qhanam/Documents/workspace_faultlocalization/ca.uwaterloo.ece.qhanam.localization/src";
	public static final String FAULTY_COVERAGE = "/Users/qhanam/Documents/workspace_repair/ca.uwaterloo.ece.qhanam.jrsrepair/cov/faulty.cov";
	public static final String SEED_COVERAGE = "/Users/qhanam/Documents/workspace_repair/ca.uwaterloo.ece.qhanam.jrsrepair/cov/seed.cov";
	
	public static final int MUTATION_CANDIDATES = 4;
	public static final int MUTATION_GENERATIONS = 2;
	public static final int MUTATION_ATTEMPTS = 5;
	
	public static final String ANT_BASE_DIR = "/Users/qhanam/Documents/workspace_faultlocalization/ca.uwaterloo.ece.qhanam.localization/";
	public static final String ANT_PATH = "/usr/bin/ant";
	public static final String ANT_TARGET = "junit";
	
	public static final long RANDOM_SEED = 1;

	public static void main(String[] args) throws Exception {
		TestExecutor testExecutor = new TestExecutor(new File(ANT_BASE_DIR), ANT_PATH, ANT_TARGET);
		JRSRepair repair = new JRSRepair(new File(SOURCE_DIRECTORY), new File(FAULTY_COVERAGE), new File(SEED_COVERAGE), MUTATION_CANDIDATES, MUTATION_GENERATIONS, MUTATION_ATTEMPTS, RANDOM_SEED, testExecutor);
		repair.buildASTs();
		repair.repair();
	}
}