package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import java.nio.file.*;
import java.util.*;

/** Nur arithmetische Referenzpruefung synthetischer Rechenbloecke, kein echter LLM-/DB-Lauf. */
public class ReferenceProbe {
    public static void main(String[] args) throws Exception {
        var result = new ArrayList<Map<String,Object>>();
        for (String path : args) {
            result.add(Map.of("datei",Path.of(path).getFileName().toString(),
                    "arithmetischeBeanstandungen", LlmCalculationReference.verifyBlocks(Files.readString(Path.of(path)))));
        }
        System.out.println(Json.write(Map.of("referenz","Java LlmCalculationReference", "synthetisch",true,"proben",result)));
    }
}
