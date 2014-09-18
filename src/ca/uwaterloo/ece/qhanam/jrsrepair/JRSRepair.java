package ca.uwaterloo.ece.qhanam.jrsrepair;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.LinkedList;
import java.util.Collection;
import java.util.Stack;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import ca.uwaterloo.ece.qhanam.jrsrepair.compiler.JavaJDKCompiler;
import ca.uwaterloo.ece.qhanam.jrsrepair.mutation.*;

public class JRSRepair {
	
	private String[] sourcePaths;
	private File faultyCoverageFile;
	private File seedCoverageFile;

	private String[] sourceFilesArray;
    private HashMap<String, DocumentASTRewrite> sourceFileContents;
    
    private HashMap<String, HashSet<String>> scope;
    
    private Statements faultyStatements;
    private Statements seedStatements;

	private LineCoverage faultyLineCoverage;
	private LineCoverage seedLineCoverage;

	private int mutationCandidates;
	private int mutationGenerations;
	private int mutationAttempts;
	
	private Random random;
	
	private JavaJDKCompiler compiler;
	private TestExecutor testExecutor;
	
	private Stack<String> patches;
	private File buildDirectory;
	
	private Integer currentMutation; // TODO: Fix this hack.
	
	/**
	 * Creates a JRSRepair object with the path to the source folder
	 * of the program we are mutating.
	 * @param sourcePaths The path to the source folder of the program we are mutating.
	 */
	public JRSRepair(String[] sourcePaths, File faultyCoverageFile, File seedCoverageFile, 
					 int mutationCandidates, int mutationGenerations, int mutationAttempts, 
					 long randomSeed, File buildDirectory, JavaJDKCompiler compiler, 
					 TestExecutor testExecutor) throws Exception {

		this.scope = new HashMap<String, HashSet<String>>();
		
		this.random = new Random(randomSeed);

		this.faultyStatements = new Statements(this.scope, this.random.nextLong());
		this.seedStatements = new Statements(this.scope, this.random.nextLong());

		this.sourcePaths = sourcePaths;
		this.faultyCoverageFile = faultyCoverageFile;
		this.seedCoverageFile = seedCoverageFile;
		
		this.mutationCandidates = mutationCandidates;
		this.mutationGenerations = mutationGenerations;
		this.mutationAttempts = mutationAttempts;
		
		this.compiler = compiler;
		this.testExecutor = testExecutor;

		/* Get the list of source files for us to mutate. */
		this.sourceFilesArray = JRSRepair.getSourceFiles(this.sourcePaths);
		
		/* Load the source code from the .java files. */
		this.sourceFileContents = JRSRepair.buildSourceDocumentMap(sourceFilesArray);
		
		/* Load the line coverage. */
		this.faultyLineCoverage = new LineCoverage(this.faultyCoverageFile);
		this.seedLineCoverage = new LineCoverage(this.seedCoverageFile);
		
		/* Initialize the patch-building stack. */
		this.patches = new Stack<String>();
		this.buildDirectory = buildDirectory;
		
		/* Initialize the compiler with context. */
		this.compiler.setContext(this.sourceFileContents, this.sourcePaths);
		
		this.currentMutation = null;
		
	}
	
	/**
	 * Builds ASTs for all the source files.
	 */
	public void buildASTs() throws Exception{
		/* Create the ASTParser with the source files to generate ASTs for, and set up the
		 * environment using ASTParser.setEnvironment.
		 */
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		
		/* setEnvironment(
		 * String[] classpathEntries,
		 * String[] sourcepathEntries, 
		 * String[] encodings,
		 * boolean includeRunningVMBootclasspath) */
		parser.setEnvironment(new String[] {}, sourceFilesArray, null, false);
		parser.setResolveBindings(false); // Throws an error when set to 'true' for some reason.
		
		/* Set up the AST handler. We need to create LineCoverage and Statements classes to store 
		 * and filter the statements from the ASTs. */
		FileASTRequestor fileASTRequestor = new MutationASTRequestor(sourceFileContents, scope, faultyLineCoverage, seedLineCoverage, faultyStatements, seedStatements);
		
		/* createASTs(
		 * String[] sourceFilePaths, 
		 * String[] encodings, - the source file encodings (e.g., "ASCII", "UTF8", "UTF-16"). Can be set to null if platform encoding is sufficient.
		 * String[] bindingKeys, 
		 * FileASTRequestor requestor, 
		 * IProgressMonitor monitor) */
		parser.createASTs(sourceFilesArray, null, new String[] {}, fileASTRequestor, null);
	}
	
	/**
	 * Attempts to repair the program using the RSRepair method.
	 * @throws Exception
	 */
	public void repair() throws Exception{
		if(this.sourceFileContents.isEmpty() || this.faultyStatements.isEmpty() || this.seedStatements.isEmpty()) throw new Exception("The ASTs have not been built.");
		
		try{
			for(int i = 0; i < this.mutationCandidates; i++) {
				System.out.println("Running candidate " + (i + 1) + " ...");
                this.mutationIteration(i + 1, 1);
			}
		}
		finally {
            System.out.println("Finished!");
		}
	}

	/**
	 * The main method for trying a mutation. It performs all the operations needed 
	 * to mutate, compile and test the program. It is recursive and will therefore
	 * attempts multiple mutations at a time before rolling back their changes. 
	 * @param generation The number of mutations that have already been applied.
	 */
	private void mutationIteration(int candidate, int generation) throws Exception{
        /* If we can't find a solution within some number of iterations, abort. */
        int attemptCounter = 0;
        
        /* Let the user know our progress. */
        System.out.println("Running generation " + generation + " ...");
        
        Mutation mutation = null;
        int compiled = -1;
        
        try{
            /* We need to ensure the first levels compile or else the rest of the
             * mutations won't be useful. */
            do {
            	compiled = -1;

                /* Get a random mutation operation to apply. */
                mutation = this.getRandomMutation();
                
                /* Apply the mutation to the AST + Document. */
                mutation.mutate();
                compiled = this.compiler.compile();
                
                try{
                    /* Compile the program and execute the test cases. */
                    if(compiled >= 0) compiled = this.testExecutor.runTests();
                } catch (Exception e){
                    System.err.println("JRSRepair: Exception thrown during test execution.");
                    System.err.println(e.getMessage());
                }
                finally { 
                    /* Roll back the current mutation. */
                    if(compiled < 0) {
                        System.out.print(" - Did not compile\n");
                        mutation.undo(); 
                    } else {
                    	this.patches.push("Candidate " + candidate + ", Generation " + generation + "\n" + mutation.toString());
                        System.out.print(" - Compiled!");
                    }
                }

                attemptCounter++;

            } while(compiled < 0 && attemptCounter < this.mutationAttempts);
            
            this.currentMutation = null;
            
            if(compiled > 0) {
                this.logSuccesfullPatch(candidate, generation);
            	System.out.print(" Passed!\n");
            }
            else if(compiled == 0) System.out.print("\n");
        
            /* Recurse to the next level of mutations. */
            if(generation < this.mutationGenerations){ 
                this.mutationIteration(candidate, generation + 1);
            }

            if(compiled >= 0) {
            	this.patches.pop();
            	mutation.undo();
            }

        } catch (Exception e) {
            /* For robustness, reset the program if this is the first generation and continue. */
        	if(generation == 1){
//                System.out.println("JRSRepair: Exception thrown during mutation recursion.");
//                System.out.println(e.getMessage());
//                this.patches.clear();
        		throw e;
        	} else {
        		throw e;
        	}
        } 
	}
	
	/**
	 * Writes the mutation operations to a file. These represent a (successful?) fix.
	 * @throws Exception
	 */
	private void logSuccesfullPatch(int candidate, int generation){
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		Date date = new Date();
		File file = new File(this.buildDirectory + "/patches", "Candidate" + candidate + "_Generation" + generation + "_" + dateFormat.format(date));
		BufferedWriter out = null;
		
		/* Store the .class files for the program so we can verify. */
		this.compiler.storeCompiled(this.buildDirectory + "/classes_Candidate" + candidate + "_Generation" + generation + "_" + dateFormat.format(date));
		
		/* Log the mutation events that produced the patch. */
        try{
            file.createNewFile();
            out = new BufferedWriter(new FileWriter(file));
            for(String s : this.patches){
                out.write(s);
            }
        } catch(Exception e) { }

        try{
            out.close();
        } catch(Exception e) { }
	}
	
	/**
	 * Returns a random mutation operation.
	 * @return A Mutation memento object for applying one mutation to a faulty statement.
	 */
	private Mutation getRandomMutation(){
		SourceStatement faultyStatement;
		Mutation mutation;
		if(this.currentMutation == null) this.currentMutation = (new Double(Math.ceil((this.random.nextDouble() * 3)))).intValue();
		
		switch(this.currentMutation){
		case 100: // Useful to make sure the program compiles.
			System.out.print("Applying null mutation...");
			mutation = new NullMutation(sourceFileContents, faultyStatements.getRandomStatement(), null);
			break;
		case 1:
			System.out.print("Applying addition mutation...");
			faultyStatement = faultyStatements.getRandomStatement();
			mutation = new AdditionMutation(sourceFileContents, faultyStatement, seedStatements.getRandomStatement(faultyStatement));
			break;
		case 2:
			System.out.print("Applying replacement mutation...");
			faultyStatement = faultyStatements.getRandomStatement();
			mutation = new ReplacementMutation(sourceFileContents, faultyStatement, seedStatements.getRandomStatement(faultyStatement));
			break;
		default:
			System.out.print("Applying deletion mutation...");
			mutation = new DeletionMutation(sourceFileContents, faultyStatements.getRandomStatement(), null);
			break;
		}
		
		return mutation;
	}
	
	/**
	 * Generates a list of java source files given a directory, or returns the
	 * file specified in an array.
	 * @param sourcePaths The path to the file/directory.
	 * @return An array of paths to Java source files.
	 * @throws Exception
	 */
	private static String[] getSourceFiles(String[] sourcePaths) throws Exception{
		Collection<File> sourceFiles = new LinkedList<File>();
		String[] sourceFilesArray = null;

		for(String sourcePath : sourcePaths){
			File sourceFile = new File(sourcePath);
			
            /* If the buggy file is a directory, get all the java files in that directory. */
            if(sourceFile.isDirectory()){
                sourceFiles.addAll(FileUtils.listFiles(sourceFile, new SuffixFileFilter(".java"), TrueFileFilter.INSTANCE));
            }
            /* The buggy file may also be a source code file. */
            else{
                sourceFiles.add(sourceFile);
            }
            
            /* Create the String array. */
            sourceFilesArray = new String[sourceFiles.size()];
            int i = 0;
            for(File file : sourceFiles){
                sourceFilesArray[i] = file.getCanonicalPath();
                i++;
            }
		}
		
		return sourceFilesArray;
	}
	
	/**
	 * Builds a HashMap with Java file paths as keys and Java file text contents as values.
	 * @param sourceFilesArray
	 * @return A HashMap containing the text of the source Java files.
	 */
	private static HashMap<String, DocumentASTRewrite> buildSourceDocumentMap(String[] sourceFilesArray) throws Exception{
		HashMap<String, DocumentASTRewrite> map = new HashMap<String, DocumentASTRewrite>();
		for(String sourceFile : sourceFilesArray){
            byte[] encoded = Utilities.readFromFile(new File(sourceFile));
            IDocument contents = new Document(new String(encoded));
            DocumentASTRewrite docrw = new DocumentASTRewrite(contents, null);
            map.put(sourceFile, docrw);
		}
		return map;
	}
	
//    /**
//     * Temp method for debugging.
//	 * @throws Exception
//	 */
//	public static void logMutation(Mutation m) throws Exception{
//		try{
//			Utilities.writeToFileAppend(new File("/Users/qhanam/Documents/workspace_faultlocalization/ca.uwaterloo.ece.qhanam.localization/log"), 
//									   m.toString().getBytes());
//		} catch (Exception e){
//			System.out.println(e.getMessage());
//			throw e;
//		}
//	}
}
