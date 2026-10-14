package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministischer DOM mit verzögerter werbin-/Abmeldeantwort; keine DB und kein Browser nötig. */
class WebLoginRegressionTest {
    @Test void erstSpaeterWiederhergestellteSitzungWirdVorErneuterAnmeldungAbgemeldet(){
        Dom dom=new Dom(true,false);Map<String,Object> context=new HashMap<>(Map.of("mitarbeiter","sb-02","passwort","richtig"));
        assertEquals("Erfolgreich angemeldet als sb-02",WebHelfer.anmeldung(dom.driver,context));
        assertEquals(1,dom.logouts);assertEquals(1,dom.logins);assertEquals("sb-02",dom.user);assertEquals("sb-02",context.get("sitzungKennung"));
    }
    @Test void verzoegertSichtbareAnmeldemaskeBrauchtKeinenLogout(){
        Dom dom=new Dom(false,false);WebHelfer.anmeldung(dom.driver,new HashMap<>(Map.of("mitarbeiter","sb-01","passwort","richtig")));
        assertEquals(0,dom.logouts);assertEquals(1,dom.logins);
    }
    @Test void negativePasswortPruefungWartetEbenfallsAufAlteSitzung(){
        Dom dom=new Dom(true,false);String result=WebHelfer.anmeldungFehlschlag(dom.driver,Map.of("mitarbeiter","sb-01"));
        assertTrue(result.startsWith("Anmeldung abgewehrt"));assertEquals(1,dom.logouts);assertEquals(1,dom.logins);assertEquals("LOGIN",dom.phase);
    }
    @Test void server500IstAuchNachWiederhergestellterSitzungKeinErfolgreicherNegativtest(){
        Dom dom=new Dom(true,true);assertThrows(IllegalStateException.class,()->WebHelfer.anmeldungFehlschlag(dom.driver,Map.of("mitarbeiter","sb-01")));
        assertEquals(1,dom.logouts);assertEquals(1,dom.logins);
    }
    private static final class Dom {
        final boolean restore,serverError;final WebDriver driver;String phase="BOOTING",user="sb-alt",password="",error="";int logouts,logins;long transition;
        Dom(boolean restore,boolean serverError){this.restore=restore;this.serverError=serverError;
            driver=(WebDriver)Proxy.newProxyInstance(WebDriver.class.getClassLoader(),new Class<?>[]{WebDriver.class,JavascriptExecutor.class},(object,method,args)->switch(method.getName()){
                case "get"->{phase="BOOTING";transition=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(100);yield null;}
                case "findElement"->element(String.valueOf(args[0]));
                case "findElements"->List.of(element(String.valueOf(args[0])));
                case "executeScript"->String.valueOf(args[0]).contains("return sessionStorage")?(phase.equals("AUTHENTICATED")?"existing-token":null):null;
                case "toString"->"Asynchroner Sitzungs-DOM";
                default->throw new UnsupportedOperationException(method.getName());
            });
        }
        void advance(){if(System.nanoTime()>=transition){if(phase.equals("BOOTING"))phase=restore?"AUTHENTICATED":"LOGIN";else if(phase.equals("LOGGING_OUT"))phase="LOGIN";}}
        boolean displayed(String locator){advance();if(locator.contains("btn-abmelden")||locator.contains("view-uebersicht"))return phase.equals("AUTHENTICATED");return phase.equals("LOGIN");}
        WebElement element(String locator){return(WebElement)Proxy.newProxyInstance(WebElement.class.getClassLoader(),new Class<?>[]{WebElement.class},(object,method,args)->switch(method.getName()){
            case "isDisplayed"->displayed(locator);
            case "clear"->{if(locator.contains("login-kennung"))user="";else if(locator.contains("login-passwort"))password="";yield null;}
            case "sendKeys"->{assertTrue(displayed(locator),"Verstecktes Feld darf nicht beschrieben werden");String value=String.join("",(CharSequence[])args[0]);if(locator.contains("login-kennung"))user=value;else if(locator.contains("login-passwort"))password=value;yield null;}
            case "click"->{assertTrue(displayed(locator),"Knopf muss nach Sitzungsinitialisierung sichtbar sein");if(locator.contains("btn-abmelden")){logouts++;phase="LOGGING_OUT";transition=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(100);}else if(locator.contains("btn-login")){logins++;if(password.equals("richtig")){phase="AUTHENTICATED";}else error=serverError?"Interner Serverfehler":"Kennung oder Passwort ist falsch";}yield null;}
            case "getText"->locator.contains("login-fehler")?error:"";
            case "getDomAttribute"->String.valueOf(args[0]).equals("data-http-status")?(serverError?"500":"400"):(serverError?"INTERNAL":"VALIDATION");
            case "toString"->locator;
            default->throw new UnsupportedOperationException(method.getName());
        });}
    }
}
