package org.shirolang.interpreter.v2;

import org.shirolang.dag.DependencyRelation;
import org.shirolang.interpreter.CodeImporter;
import org.shirolang.interpreter.ast.IncludeStatement;
import org.shirolang.interpreter.ast.Program;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Visit the include statements
 */
public class IncludeVisitor extends BaseVisitor{
    private Set<DependencyRelation<Path>> sourceFiles;
    private Path parentDirectory;
    private Path sourceFile;
    private URL stdLib;
    private SymbolTable symbolTable;

    public IncludeVisitor(SymbolTable lib, Path source) {
        super();
        sourceFiles = new HashSet<>();
        this.sourceFile = source;
        this.parentDirectory = source.getParent();
        this.symbolTable = lib;
        stdLib = CodeImporter.class.getResource("lib"); //TODO update to new class when migrating to v2
    }

    public Set<DependencyRelation<Path>> visit(Program p){
        for (IncludeStatement include: p.getIncludes()){
            sourceFiles.addAll(visit(include));
        }

        return sourceFiles;
    }

    public Set<DependencyRelation<Path>> visit(IncludeStatement include){
        String importedFile = include.getFile();
        // look in standard symbolTable folders first (org.shirolang.interpreter.lib)

        if(!importedFile.endsWith(".sro")){
            importedFile = importedFile + ".sro";
        }

        try{
            Path standardLib = Paths.get(stdLib.toURI()).resolve(importedFile);
            Path sourceAtRoot = parentDirectory.resolve(importedFile);
            Path sourceInLib = parentDirectory.resolve("lib").resolve(importedFile);


            if(Files.exists(standardLib)){
                sourceFiles.add(new DependencyRelation<>(sourceFile, standardLib));
                sourceFiles.addAll(getSourceDependencies(standardLib));
            }
            else if (Files.exists(sourceAtRoot)) {
                sourceFiles.add(new DependencyRelation<>(sourceFile, sourceAtRoot));
                sourceFiles.addAll(getSourceDependencies(sourceAtRoot));

            } else if (Files.exists(sourceInLib)) {
                sourceFiles.add(new DependencyRelation<>(sourceFile, sourceInLib));
                sourceFiles.addAll(getSourceDependencies(sourceInLib));
            }else{
                errors.add(new IncludeNotFoundError(importedFile));
            }
        } catch (IOException | URISyntaxException ex) {
            Logger.getLogger(IncludeVisitor.class.getName()).log(Level.SEVERE, null, ex);
        }

        return sourceFiles;
    }

    /**
     * Gets the sources files the source depends on
     * @param file source file to get the dependents of
     * @return a set containing the dependency relationships for each file {@code file} depends on
     * @throws IOException
     */
    private Set<DependencyRelation<Path>> getSourceDependencies(Path file) throws IOException {
        IncludeVisitor visitor = new IncludeVisitor(symbolTable, file);
        return visitor.visit(symbolTable.buildAST(file));
    }
}
