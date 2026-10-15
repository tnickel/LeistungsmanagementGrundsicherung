import de.allgemein.lmg.web.Json;
public class JsonReviewProbe {
  static void check(String name,String json,boolean expected) {
    boolean accepted; try{Json.parse(json);accepted=true;}catch(IllegalArgumentException e){accepted=false;}
    if(accepted!=expected)throw new AssertionError(name+" actual="+accepted);
    System.out.println("JSON name="+name+" accepted="+accepted+" expected="+expected);
  }
  public static void main(String[] args) {
    check("empty-map","{}",true); check("empty-list","[]",true);
    check("duplicate-null-key","{\"a\":null,\"a\":1}",false);
    check("duplicate-decoded-key","{\"a\":1,\"\\u0061\":2}",false);
    check("surrogate-pair","\"\\ud83d\\ude00\"",true);
    check("isolated-surrogate","\"\\ud83d\"",false);
    check("bad-unicode-escape","\"\\u+001\"",false);
    check("raw-tab","\"a\tb\"",false);
    check("non-json-space","\u00a01",false);
    check("leading-zero","01",false);check("trailing-comma","[1,]",false);
    check("digits64","1".repeat(64),true);check("digits65","1".repeat(65),false);
    check("exponent999","1e999",true);check("exponent1000","1e1000",false);
    check("depth128","[".repeat(128)+"]".repeat(128),true);
    check("depth129","[".repeat(129)+"]".repeat(129),false);
    check("many-empty-containers","["+"{},".repeat(1000)+"{}]",true);
  }
}
