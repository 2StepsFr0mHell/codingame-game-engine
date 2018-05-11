package com.codingame.gameengine.runner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * Parse an HTML file and remove blocks between <!-- BEGIN keyword1 keyword2 --> and <!-- END -->
 * Must contain a block <!-- LEAGUES level1 level2 level3 level4 -->
 * 
 * The HTML file(s) must be named "statement_<language>.html.tpl" and placed in "config" directory
 */

public class StatementSplitter {

    private static final Pattern LEAGUE_MARKER = Pattern.compile("\\s*<!--\\s+LEAGUES\\s+(?<leagues>.+)\\s+-->\\s*");
    private static final Pattern BEGIN_MARKER = Pattern.compile("\\s*<!--\\s+BEGIN\\s+(?<leagues>.+)\\s+-->\\s*");
    private static final Pattern END_MARKER = Pattern.compile("\\s*<!--\\s+END\\s+-->\\s*");

    public static void generateSplittedStatement(Path sourceFolderPath, File statementFile, ExportReport exportReport) {
        //Retrieve content from the file
        List<String> lines = getLines(statementFile);

        List<String> leagues = getLeagues(lines);

        if (leagues.isEmpty()) {
            exportReport.addItem(ReportItemType.WARNING, statementFile.getName() + ": Statement splitter did not found leagues");
            return;
        }

        for (String league : leagues) {
            if (!sourceFolderPath.resolve("config/" + league).toFile().isDirectory()) {
                sourceFolderPath.resolve("config/" + league).toFile().mkdir();
            }

            writeStatement(sourceFolderPath, statementFile, lines, league, exportReport);
        }
    }

    private static void writeStatement(Path sourceFolderPath, File statementFile, List<String> lines, String league, ExportReport exportReport) {
        Writer writer = null;
        try {
            //Substring to remove ".tpl" from the new file
            String newStatementFilename = statementFile.getName().substring(0, statementFile.getName().length() - 4);
            File newFile = sourceFolderPath.resolve("config/" + league + "/" + newStatementFilename).toFile();
            if (!newFile.exists()) {
                newFile.createNewFile();
            }

            writer = new BufferedWriter(
                new OutputStreamWriter(
                    new FileOutputStream(newFile)
                )
            );
            writer.write("<!-- Autogenerated file! Do not edit directly. -->");

            boolean visible = true;
            Stack<String> stack = new Stack<>();

            for (String line : lines) {
                Matcher beginMatcher = BEGIN_MARKER.matcher(line);
                if (beginMatcher.matches()) {
                    stack.push(beginMatcher.group("leagues"));
                    visible = Arrays.asList(beginMatcher.group("leagues").split(" ")).contains(league);
                } else if (END_MARKER.matcher(line).matches()) {
                    if (stack.isEmpty()) {
                        exportReport.addItem(ReportItemType.WARNING, league + "/" + newStatementFilename + ": \"" + line + "\" unexpected");
                        writer.close();
                        return;
                    }
                    stack.pop();
                    visible = stack.isEmpty() ? true : Arrays.asList(stack.peek().split(" ")).contains(league);
                } else if (visible) {
                    writer.write(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not generate statement file", e);
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException("Could not close statement file", e);
            }
        }
    }

    private static List<String> getLeagues(List<String> lines) {
        Matcher leagueMatcher;
        List<String> leagues = new ArrayList<>();
        for (String line : lines) {
            leagueMatcher = LEAGUE_MARKER.matcher(line);
            if (leagueMatcher.matches()) {
                leagues.addAll(Arrays.asList(leagueMatcher.group("leagues").split(" ")));
                break;
            }
        }
        return leagues;
    }

    private static List<String> getLines(File statementFile) {
        List<String> lines = new ArrayList<>();
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(statementFile));

            String statementContent = null;
            while ((statementContent = bufferedReader.readLine()) != null) {
                lines.add(statementContent + '\n');
            }
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }
}