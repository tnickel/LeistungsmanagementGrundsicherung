package de.allgemein.testsweet;
import de.allgemein.lmg.web.Json;
import java.nio.file.*;
import java.util.*;

/** Beide Validatoren erhalten dieselben synthetischen Bytes; kein API-Aufruf. */
public class ValidatorParityProbe {
    public static void main(String[] args) throws Exception {
        var ids=List.of("POSITIV:review:TF1","NEGATIV:review:TF2");
        List<Map<String,Object>> requirements=ids.stream().map(id->Map.<String,Object>of("caseId",id,"calculations",List.of(),"checkIds",List.of("V1"),"sourceFindings",List.of(),"missingInputs",List.of())).toList();
        var cases=ids.stream().map(id->Map.of("caseId",id,"verdict","PASS","reasoning","Synthetic check reviewed","findings",List.of(),"calculations",List.of(),"checks",List.of(Map.of("checkId","V1","verdict","PASS","reasoning","Synthetic workflow verified")))).toList();
        String raw=Json.write(Map.of("schemaVersion",2,"caseIds",ids,"cases",cases));
        List<Map<String,Object>> variants=List.of(Map.of("name","raw","content",raw),Map.of("name","markdown-fence","content","```json\n"+raw+"\n```"),Map.of("name","fractional-version","content",raw.replace("\"schemaVersion\":2","\"schemaVersion\":2.9")));
        List<Map<String,Object>> javaResults=new ArrayList<>();
        for(var variant:variants){boolean accepted;try{LlmAnswerValidator.validate((String)variant.get("content"),ids,requirements);accepted=true;}catch(Exception e){accepted=false;}javaResults.add(Map.of("name",variant.get("name"),"accepted",accepted));}
        Path out=Path.of(args[0]);
        Files.writeString(out.resolve("validator-fixture.json"),Json.write(Map.of("ids",ids,"requirements",requirements,"variants",variants)));
        Files.writeString(out.resolve("validator-java-results.json"),Json.write(javaResults));
        System.out.println(Json.write(javaResults));
    }
}
