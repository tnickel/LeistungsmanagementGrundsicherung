// Lesefassung für fachfremde Entscheider; Bewertung und Belegprüfung bleiben in runs.js.
const fs=require("fs"),path=require("path");
const {Document,Paragraph,TextRun,Table,TableRow,TableCell,WidthType,BorderStyle,ShadingType,
  HeadingLevel,Footer,Header,AlignmentType,PageNumber,ImageRun,ExternalHyperlink,VerticalAlign,TableLayoutType}=require("./report-library").docx;
const runs=require("./runs"),content=require("./report-content"),{reportMetrics}=require("./report-summary");
const FONT="Calibri",INK="000000",BODY="202B33",MUTED="52616B",NAVY="173A4A";
const WIDTH=9412;
const KAPITEL=["Einleitung","1 Testarten und ihre Aufgaben","2 Zwei Prüfwege für dieselbe Fachsoftware",
  "3 Herkunft der Testfälle und ihrer Sollwerte","4 Quellenbasierte Modellfälle",
  "5 Unabhängige Gegenrechnung mit einem Online-Rechner","6 Transaktionssteuerung bei gleichzeitiger Arbeit",
  "7 Ergebnisübersicht und Einordnung der Prüfläufe","8 Fazit und nächste Schritte","Gesamtbewertung des Prüfumfangs"];
const clean=value=>String(value??"").replace(/[\u2011\u2013\u2014]/g,"-");
const format=value=>Number(value).toLocaleString("de-DE");
function text(value,options={}){return new TextRun({text:clean(value),font:FONT,size:22,color:BODY,...options});}
function paragraph(value,options={}){return new Paragraph({spacing:{after:135,line:290},widowControl:true,
  children:[text(value)],...options});}
function heading(value,level=1,pageBreakBefore=false){return new Paragraph({heading:level===1?HeadingLevel.HEADING_1:HeadingLevel.HEADING_2,
  pageBreakBefore,keepNext:true,spacing:{before:level===1?340:220,after:140},children:[text(value,{bold:true,size:level===1?32:25,color:INK})]});}
function note(label,value){return new Paragraph({spacing:{before:80,after:150,line:275},widowControl:true,
  children:[text(label+": ",{bold:true,color:INK}),text(value)]});}
function sourceLink(label,url,description){return new Paragraph({spacing:{after:130,line:275},widowControl:true,
  children:[new ExternalHyperlink({link:url,children:[text(label,{color:"18566B",underline:{}})]}),text(". "+description)]});}
function fill(value,metrics){
  if(!metrics.complete && /\{\{(?:plannedTests|executedTests|skippedTests|failedTests)\}\}/.test(value))
    return "Für einen vollständigen gemeinsamen Zähler fehlen archivierte Laufbelege. Die vorhandenen Zahlen werden in Kapitel 1 je Bereich gezeigt; eine Teilzählung wird nicht als Gesamtumfang ausgegeben. Ein automatisierter Prüffall kann eine kleine Rechenregel oder einen größeren technischen Ablauf untersuchen. Seine Zählung ist deshalb keine reine Unit-Test-Zahl.";
  return clean(value).replace(/\{\{(\w+)\}\}/g,(_,key)=>metrics.placeholders[key]??"");
}
function blocks(name,metrics){return (content[name]||[]).flatMap(block=>[
  ...(block.title?[heading(block.title.replace(/[?:]/g,""),2)]:[]),...block.paragraphs.map(value=>paragraph(fill(value,metrics)))]);}
function figure(file,caption){
  const png=path.join(__dirname,"diagram-assets",file+".png");
  if(!fs.existsSync(png))throw Error("Reportdiagramm fehlt: "+file);
  const dimensions={"01-pruefebenen":[1600,830],"02-fallentstehung":[1600,850],"03-pruefablauf":[1600,770],"04-stress-zwei-wellen":[1600,950]};
  const [width,height]=dimensions[file],display=616;
  return [new Paragraph({alignment:AlignmentType.CENTER,keepNext:true,spacing:{before:150,after:70},children:[new ImageRun({type:"png",data:fs.readFileSync(png),
      transformation:{width:display,height:Math.round(display*height/width)},altText:{title:caption,description:caption,name:file}})]}),
    paragraph(caption,{spacing:{after:180,line:250},children:[text(caption,{size:19,color:MUTED})]})];
}
function table(headers,rows,widths,alignments=[]){
  if(widths.reduce((a,b)=>a+b,0)!==WIDTH)throw Error("Ungültige Tabellenbreite");
  const border={style:BorderStyle.SINGLE,size:4,color:"D9D9D9"};
  function cell(value,index,header,rowIndex){
    const isNumber=alignments[index]==="number",color=/^(BESTANDEN|Aktuell belegt)$/.test(String(value))?"166436":/FEHL|NICHT BESTANDEN/.test(String(value))?"9C2525":BODY;
    return new TableCell({width:{size:widths[index],type:WidthType.DXA},verticalAlign:VerticalAlign.CENTER,
      shading:{type:ShadingType.CLEAR,fill:header?NAVY:rowIndex%2?"F0F4F6":"FFFFFF"},
      margins:{top:115,bottom:115,left:120,right:120},children:[new Paragraph({spacing:{after:0,line:245},keepNext:header || rows.length<=7 && rowIndex<rows.length-1,
        alignment:header?AlignmentType.LEFT:isNumber?AlignmentType.CENTER:AlignmentType.LEFT,
        children:[text(value,{size:20,color:header?"FFFFFF":color,bold:header||index===rows[0]?.length-1})]})]});
  }
  return new Table({width:{size:WIDTH,type:WidthType.DXA},columnWidths:widths,layout:TableLayoutType.FIXED,
    borders:{top:border,bottom:border,left:border,right:border,insideHorizontal:border,insideVertical:border},
    rows:[new TableRow({tableHeader:true,cantSplit:true,children:headers.map((v,i)=>cell(v,i,true,0))}),
      ...rows.map((row,n)=>new TableRow({cantSplit:true,children:row.map((v,i)=>cell(v,i,false,n))}))]});
}
function spacer(){return new Paragraph({spacing:{after:100},children:[]});}
function stateLabel(state){return ({PASS:"BESTANDEN",FAIL:"FEHLGESCHLAGEN",ABORTED:"ABGEBROCHEN",RUNNING:"LÄUFT",UNVERIFIED:"UNVOLLSTÄNDIG"})[state]||"UNVOLLSTÄNDIG";}
function layerName(id){
  if(id.startsWith("JUnit:"))return id.includes("Hauptprojekt")?"Automatische Prüfungen der Fachanwendung":"Automatische Prüfungen des Simulators";
  if(id.startsWith("GLM:"))return "Zusätzliche KI-Nachrechnung";
  const [mode,category]=id.split("|"),names={POSITIV:"Erfolgsfälle",NEGATIV:"Erwartete Ablehnungen",ECHT:"Quellenbasierte Modellfälle",RECHNER:"Rechner-Vergleichsfälle",LAST:"Viele parallele Vorgänge",STRESS:"Zwei Bearbeitungswellen"};
  return category?(mode==="WEB"?"Browserweg ":"Direkter Fachweg ")+(names[category]||category):id;
}
function moduleCounts(metrics){
  const rows=metrics.modules.map(({name,recorded})=>[name==="Hauptprojekt"?"Fachanwendung":"Simulator testsweet",
    ...["tests","executed","passed","skipped","failures"].map(key=>recorded?format(key==="failures"?recorded.failures+recorded.errors:recorded[key]):"nicht belegt")]);
  if(metrics.complete)rows.push(["Gesamt",...['tests','executed','passed','skipped'].map(key=>format(metrics.totals[key])),format(metrics.totals.failures+metrics.totals.errors)]);
  return [table(["Bereich","Erfasst","Ausgeführt","Erfolgreich","Ausgelassen","Fehler"],rows,[3012,1280,1280,1280,1280,1280],["","number","number","number","number","number"]),spacer()];
}
function cover(repo,date,id,metrics,stand){
  const result=[new Paragraph({style:"Title",spacing:{before:380,after:200},children:[text("Prüfbericht zur Softwarequalität",{size:60,bold:true,color:INK})]}),
    paragraph("SDN.Leistung Leistungsmanagement Grundsicherung",{spacing:{after:250},children:[text("SDN.Leistung Leistungsmanagement Grundsicherung",{size:31,color:INK})]}),
    paragraph("Verständlicher Überblick über Prüfverfahren, Testfälle und Ergebnisse",{spacing:{after:290},children:[text("Verständlicher Überblick über Prüfverfahren, Testfälle und Ergebnisse",{size:25,color:INK})]})];
  result.push(...figure("01-pruefebenen","Abbildung 1 Einzelne Bausteine, vollständige Arbeitsabläufe und Browserbedienung ergänzen sich. Die Datenbankkontrolle zeigt, was tatsächlich gespeichert wurde."));
  result.push(paragraph("Softwareversion 0.5.8 · Bericht vom "+date+" · Kennung "+id,{children:[text("Softwareversion 0.5.8 · Bericht vom "+date+" · Kennung "+id,{size:20,color:MUTED})]}),
    paragraph("Dieser Bericht richtet sich an Verantwortliche aus Verwaltung, Fachbereich und Projektleitung. Er erklärt, wie wir Fehler suchen, woher unsere Testfälle stammen und wie die Ergebnisse zu lesen sind."),
    note("Gesamtbewertung zum Berichtstand",stand.verdict+". Die Einordnung und noch fehlende Nachweise werden im Ergebniskapitel erläutert."),
    note("Dokumentierter automatischer Prüflauf",metrics.complete?format(metrics.totals.tests)+" erfasste Prüffälle, davon "+format(metrics.totals.executed)+" ausgeführt und "+format(metrics.totals.skipped)+" ausgelassen. Die Zählung umfasst Unit-Tests und Prüfungen größerer technischer Abläufe.":"Für eine gemeinsame Laufzählung fehlen vollständige archivierte Belege."));
  return result;
}
function contents(pages){return [heading("Inhaltsverzeichnis",1,true),...KAPITEL.map((title,i)=>new Paragraph({spacing:{after:140,line:270},
  tabStops:[{type:"right",position:WIDTH-80,leader:"dot"}],children:[text(title+"\t",{size:21,color:INK}),text(pages?String(pages[i]):"00",{size:21,color:INK})]}))];}
function kapitelEinleitung(metrics,date){return [heading(KAPITEL[0],1,true),...blocks("introduction",metrics),
  note("So lesen Sie den Bericht","Die ersten Kapitel erklären die Prüfmethoden mit Beispielen. Danach folgen die dokumentierten Zahlen und ihre Bewertung. Eine ausgeführte Prüfung, ein erfolgreicher Prüffall und ein vollständiger aktueller Nachweis sind drei unterschiedliche Angaben."),
  paragraph("Berichtsdatum "+date+". Zahlen werden aus den gespeicherten Prüflaufbelegen übernommen; die aktuelle Bewertung wird zusätzlich gegen den Programmstand geprüft.")];}
function unitChapter(metrics){return [heading(KAPITEL[1],1,true),...blocks("unit",metrics),heading("Wie viele automatisierte Prüffälle dokumentiert sind",2),
  paragraph("Die folgende Zählung bezieht sich je Bereich auf genau einen vollständig archivierten Prüflauf. JUnit ist das Werkzeug, das diese Prüfungen ausführt. Unter seinem gemeinsamen Zähler stehen sowohl kleine Bausteinprüfungen als auch größere technische Abläufe."),
  ...moduleCounts(metrics),paragraph("Erfasst bedeutet im Prüflauf aufgeführt. Ausgeführt heißt tatsächlich abgearbeitet. Erfolgreich heißt mit erfüllter Erwartung abgeschlossen. Ausgelassen bedeutet nicht ausgeführt und trägt keinen Erfolgsnachweis bei. Bekannte Fehler werden gesondert gezählt."),
  paragraph("Die Zähler stammen aus den Laufbelegen vom "+[...new Set(metrics.modules.filter(x=>x.recorded).map(x=>runs.deutschDatum(x.recorded.date)))].join(" und ")+". Sie bleiben als dokumentierter Laufumfang lesbar, auch wenn sich danach der Programm- oder Reportstand ändert. Ob sie den aktuellen Stand vollständig bestätigen, zeigt Kapitel 7.")];}
function routesChapter(metrics){return [heading(KAPITEL[2]),...blocks("routes",metrics),
  ...figure("03-pruefablauf","Abbildung 2 Ein Testfall durchläuft die Anwendung. Danach werden die gespeicherten Daten direkt gelesen und mit der Erwartung verglichen. Eine grüne Bildschirmmeldung allein reicht nicht.")];}
function generationChapter(metrics){return [heading(KAPITEL[3]),...blocks("generation",metrics),
  ...figure("02-fallentstehung","Abbildung 3 Zufallskombinationen und feste Grenzfälle sind künstlich zusammengestellt. Öffentliche Quellen liefern Bausteine für Modellfälle. Ein fremder Rechner ergänzt Vergleichswerte als zusätzliche Gegenprobe."),
  heading("Der feste Bestand und die erzeugten Kombinationen",2),
  table(["Testfallgruppe","Wie sie entsteht","Fester Rahmen"],[
    ["Positive Abläufe","10 Grundfälle, 9 feste Grenzfälle und zusätzliche Kombinationen; jeder 50. Auftrag ist ein Kassenlauf","Bis 500 Aufträge pro Lauf"],
    ["Erwartete Ablehnungen","9 feste Abwehrfälle, danach erzeugte Kombinationen aus vier Verstoßgruppen","Bis 500 Aufträge pro Lauf"],
    ["Quellenbasierte Modellfälle ECHT","Öffentliche Zahlen und Regeln, ergänzt um ausdrücklich genannte Modellannahmen","5 feste Fälle"],
    ["Rechner-Vergleichsfälle RECHNER","Feste nachrechenbare Konstellationen; dokumentierte externe Gegenproben für 1, 6 und 12","12 feste Fälle"]
  ],[2370,4942,2100]),spacer(),
  paragraph("Ein vollständiger positiver 500er Topf enthält damit 10 Grundfälle, 9 feste Grenzfälle, 10 Kassenläufe und 471 weitere generierte Kombinationen. Die feste Quote verhindert, dass wichtige Grenzfälle in einer großen Zufallsmenge zufällig fehlen.")];}
function sourcedChapter(metrics){return [heading(KAPITEL[4]),...blocks("sourcedCases",metrics),
  heading("Welche fünf Modellkonstellationen geprüft werden",2),
  table(["Konstellation","Öffentlicher Baustein","Was der Modellfall prüft"],[
    ["Paar mit Wohnkosten","Regelbedarfsstufen und kommunale Unterkunftswerte","Fester Zahlbetrag 1.542,00 EUR"],
    ["Historischer Vermögenswert","44.500 EUR aus einem berichteten BSG Fall","Technische Ablehnung bei im Modell bedarfsdeckendem Vermögen"],
    ["Kleines Kontoguthaben","Verbandsbeispiel mit 800 EUR","1.023,00 EUR Zahlbetrag ohne Vermögenseinsatz"],
    ["Historischer Fahrzeugwert","31.000 EUR Gesamtwert aus einem veröffentlichten Fallbericht","Technische Vermögensabwehr; heutiger Kfz Schutz ist keine geprüfte Vollregel"],
    ["Gewählter Unterkunftsdeckel","Aus örtlichen Einzelwerten abgeleitete 590 EUR","1.153,00 EUR im gewählten Gesamtdeckelmodell"]
  ],[2250,3462,3700]),spacer(),
  paragraph("Die Quellen und Ergänzungen sind im Belegverzeichnis des Projekts dokumentiert. Amtliche Regelwerte stammen aus der Anlage zu § 28 SGB XII. Örtliche Werte stammen aus der Richtlinie des Altenburger Landes. Der Lebenshilfe-Ratgeber liefert Vermögensbeispiele; Gerichtsbezüge dienen jeweils als Herkunft einzelner Fallzahlen."),
  heading("Quellen zum Nachlesen",2),
  sourceLink("Amtliche Regelbedarfsstufen","https://www.gesetze-im-internet.de/sgb_12/anlage.html","Ausgangswerte für die festgelegten Bedarfssätze."),
  sourceLink("Richtlinienanlage des Altenburger Landes","https://sessionnet.altenburgerland.de/bi/getfile.php?id=52833&type=do","Örtliche Wohnkostenwerte als Bausteine der Modellfälle."),
  sourceLink("Lebenshilfe-Ratgeber zur Grundsicherung","https://www.lebenshilfe.de/informieren/arbeiten/grundsicherung-im-alter-und-bei-erwerbsminderung","Herkunft des 800-EUR-Beispiels und des historischen Fahrzeugwerts."),
  sourceLink("Verwendeter Sekundärbericht zum BSG-Fall","https://sozialrat.org/bsg-urteil-grundsicherung-alter-27-mai-2026-vermoegensuebergangsregel/","Herkunft der Summe von 44.500 EUR. Daraus wurde keine vollständige unabhängige Monatsberechnung gewonnen.")];}
function calculatorChapter(metrics){return [heading(KAPITEL[5]),...blocks("calculator",metrics),
  heading("Ein Vergleichsfall Schritt für Schritt",2),
  paragraph("Die folgende feste Modellrechnung zeigt den Vergleichsfall 12. Zwei erwachsene Partner leben gemeinsam; angesetzt sind 345 EUR Miete, 70 EUR Heizung und 500 EUR Erwerbseinkommen. Weitere Besonderheiten sind für diesen Vergleich ausdrücklich nicht enthalten."),
  table(["Rechenschritt","Betrag in EUR"],[
    ["Regelbedarf für zwei Partner 2 × 506","1.012,00"],["Miete und Heizung 345 + 70","415,00"],["Gesamter Bedarf","1.427,00"],
    ["Erwerbseinkommen","500,00"],["Davon im Modell frei 30 Prozent","150,00"],["Anzurechnendes Einkommen 500 - 150","350,00"],
    ["Erwarteter Zahlbetrag 1.427 - 350","1.077,00"]
  ],[7012,2400],["","number"]),spacer(),
  paragraph("Für diesen Fall liegen neben der Handrechnung eine externe Browsergegenprobe und die Prüfung des gespeicherten Zahlbetrags vor. Die Übereinstimmung erhöht die Sicherheit für diese konkrete Konstellation. Sie belegt nicht automatisch alle denkbaren persönlichen oder rechtlichen Besonderheiten."),
  sourceLink("Verwendeter Online-Rechner","https://www.steuerschroeder.de/Steuerrechner/Grundsicherung.html","Die dokumentierten Eingabe- und Ergebnisbelege vom 27.10.2026 betreffen die Fälle 1, 6 und 12.")];}
function parallelChapter(metrics){return [heading(KAPITEL[6]),...blocks("parallel",metrics),
  ...figure("04-stress-zwei-wellen","Abbildung 4 Im Stresstest müssen zuerst alle Vorgänge angelegt sein. Erst nach dieser gemeinsamen Barriere beginnt die zweite Bearbeitungswelle mit denselben Vorgängen.")];}
function reasonText(reasons){
  const unique=[...new Set(reasons||[])],missing=unique.filter(value=>/^JUnit-Testmethode fehlt:/.test(value));
  const readable=unique.filter(value=>!/^JUnit-Testmethode fehlt:/.test(value)).map(value=>clean(value)
    .replace(/Getesteter Build ist nicht aktuell gebunden/g,"Der getestete Programmstand ist nicht als identisch mit dem heutigen Stand bestätigt")
    .replace(/Getesteter Software-\/Buildstand ist veraltet oder nicht gebunden/g,"Die KI-Gegenprüfung ist nicht als gültiger Nachweis für den heutigen Programmstand bestätigt")
    .replace(/JUnit-Testklasseninventar ist unvollständig\/unerwartet/g,"Die Zuordnung zwischen den vorgesehenen automatischen Prüfungen und ihren aktuellen Belegen ist unvollständig")
    .replace(/JUnit-Buildbindung fehlt oder ist veraltet/g,"Die automatische Prüfung ist nicht aktuell an den heutigen Programmstand gebunden")
    .replace(/Pflichtlauf fehlt/g,"Für diese vorgesehene Prüfung liegt kein Laufbeleg vor"));
  if(missing.length)readable.push("Für "+format(missing.length)+" im aktuellen Prüfinventar erwartete Methoden fehlt ein aktuell gültiger Einzelbeleg. Das bezeichnet eine Nachweislücke, keine entsprechende Zahl neu gefundener Programmfehler");
  return readable.join(". ")+".";
}
function evidenceNotes(layers){
  const groups=new Map();
  for(const layer of layers.filter(x=>x.state!=="PASS")){
    const reason=reasonText(layer.reasons),group=groups.get(reason)||[];group.push(layer);groups.set(reason,group);
  }
  const allSimulators=layers.filter(x=>x.id.includes("|"));
  return [...groups].map(([reason,group])=>{
    const label=group.length===1?layerName(group[0].id):group.length===allSimulators.length && group.every(x=>x.id.includes("|"))?
      "Alle "+format(group.length)+" Simulator-Prüfungen":group.map(x=>layerName(x.id)).join(", ");
    return note(label,reason);
  });
}
function ergebnisTabelle(repo,stand=runs.reportStand(repo),metrics=reportMetrics(repo,stand)){
  const rows=stand.layers.map(layer=>{
    const item=layer.evidence,isJunit=layer.id.startsWith("JUnit:"),isGlm=layer.id.startsWith("GLM:");
    const recorded=isJunit?metrics.modules.find(x=>layer.id.endsWith(x.name))?.recorded:null;
    const glmCount=isGlm && item?.evidenceVerified ? /^(\d+)\/(\d+) vollständige Fälle/.exec(item.meta?.pruefumfang||"") : null;
    const total=isJunit?recorded?.tests:isGlm?glmCount?Number(glmCount[2]):undefined:item?.gesamt;
    const processed=isJunit?recorded?.executed:isGlm?glmCount?Number(glmCount[1]):undefined:item?.verifiziert;
    const date=isJunit?recorded?.date:isGlm?item?.datum:item?.startedAt;
    return [layerName(layer.id),runs.deutschDatum(date),Number.isInteger(total)?format(total):"nicht belegt",Number.isInteger(processed)?format(processed):"nicht belegt",stateLabel(layer.state)];
  });
  return [heading(KAPITEL[7],1,true),paragraph("Die Tabelle verbindet zwei unterschiedliche Fragen: Welcher Lauf ist dokumentiert und reicht sein Nachweis für den heutigen Stand aus? Ein früher erfolgreicher Lauf bleibt nachvollziehbar. Nach einer Programmänderung darf er jedoch nicht ungeprüft als aktuelle Bestätigung weitergelten."),
    paragraph("Bei automatisierten Prüfungen bedeutet die Spalte Bearbeitet tatsächlich ausgeführt. Bei Simulatorfällen bedeutet sie mit SQL am gespeicherten Endzustand kontrolliert. Die Spalte Nachweis ist die strengere aktuelle Bewertung und ergibt sich nicht allein aus einer hohen Fallzahl."),
    table(["Prüfung","Laufdatum","Erfasst","Bearbeitet","Nachweis"],rows,[3150,1330,1010,1302,2620],["","","number","number",""]),spacer(),
    heading("Was die Bewertungen bedeuten",2),
    note("BESTANDEN","Diese Prüfebene ist vollständig erfolgreich und für den aktuellen Stand belegt."),
    note("FEHLGESCHLAGEN","Mindestens ein bekannter Testfehler oder eine fehlerhafte Fachwirkung wurde festgestellt. Das ist vor einer Freigabe zu klären."),
    note("UNVOLLSTÄNDIG","Es fehlt ein ausreichender aktueller Nachweis. Dazu gehören ausgelassene Prüfungen, ältere Programmstände und fehlende Unterlagen. Diese Bezeichnung allein beweist keinen Fehler der Fachsoftware."),
    note("ABGEBROCHEN oder LÄUFT","Der Versuch hat noch keinen vollständigen Abschluss. Ein dabei bereits bekannter Fehler bleibt trotzdem ein Fehler."),
    heading("Warum einzelne Nachweise noch nicht ausreichen",2),
    ...evidenceNotes(stand.layers),
    paragraph("Die KI-Nachrechnung ist eine zusätzliche Stichprobe aus positiven und negativen Fällen. Die fünf quellenbasierten Modellfälle und zwölf Rechner-Vergleichsfälle werden über eigene Fachläufe geprüft. Die automatische Zertifikatssuite umfasst weiterhin ihre sieben ausdrücklich festgelegten Kombinationen; sie enthält diese beiden zusätzlichen Kategorien nicht.")];
}
function kapitelFazit(repo,stand=runs.reportStand(repo),metrics=reportMetrics(repo,stand)){
  return [heading(KAPITEL[8]),paragraph("Gesamtbewertung: "+stand.verdict+". Für "+metrics.currentLayers+" von "+metrics.requiredLayers+" erklärten Prüfebenen liegt eine vollständige aktuelle Bestätigung vor."),
    ...blocks("conclusion",metrics),
    paragraph(stand.verdict==="BESTANDEN"?"Alle in Kapitel 7 aufgeführten Prüfebenen erfüllen die dort beschriebenen Anforderungen. Die Aussage gilt für den dokumentierten Umfang und die benannten fachlichen Modellgrenzen.":"Eine vollständige positive Bestätigung für den gesamten erklärten Umfang ist mit diesem Nachweisstand noch nicht möglich. Für eine Freigabe sind bekannte Fehler zu klären und die im Ergebniskapitel genannten fehlenden aktuellen Prüfungen nachzuholen."),
    ...blocks("glossary",metrics)];
}
function zertifikatsSeite(date,id,repo,stand=runs.reportStand(repo),metrics=reportMetrics(repo,stand)){
  return [heading(KAPITEL[9],1,true),paragraph("Diese abschließende Bewertung bezieht sich auf die im Ergebniskapitel einzeln ausgewiesenen Prüfungen. Sie bestätigt keine zusätzlichen Prüfungen und keine weitergehende fachliche Abdeckung."),
    note("Gesamtbewertung",stand.verdict),
    paragraph(metrics.currentLayers+" von "+metrics.requiredLayers+" Prüfebenen sind aktuell vollständig belegt. Das Gesamturteil lautet nur dann BESTANDEN, wenn jede vorgesehene Ebene vollständig bestätigt ist. Ein bekannter Fehler führt zu NICHT BESTANDEN; fehlende aktuelle Nachweise führen zu UNVOLLSTÄNDIG."),
    paragraph("Berichtsdatum "+date+". Reportkennung "+id+". Dieser Report erklärt den Prüfstand. Der gesonderte formale Zertifikatsnachweis ist das Dokument SDN Leistung LMG Testzertifikat."),
    note("Für die praktische Entscheidung","Lesen Sie die Zahlen zusammen mit den noch offenen Nachweisen in Kapitel 7. Viele erfolgreiche Einzelprüfungen sind wertvoll; sie ersetzen keine fehlende Prüfung eines anderen Bereichs.")];
}
function baueDokument(repo,date,id,pages,stand=runs.reportStand(repo)){
  const metrics=reportMetrics(repo,stand),children=[...cover(repo,date,id,metrics,stand),...contents(pages),...kapitelEinleitung(metrics,date),
    ...unitChapter(metrics),...routesChapter(metrics),...generationChapter(metrics),...sourcedChapter(metrics),...calculatorChapter(metrics),...parallelChapter(metrics),
    ...ergebnisTabelle(repo,stand,metrics),...kapitelFazit(repo,stand,metrics),...zertifikatsSeite(date,id,repo,stand,metrics)];
  return new Document({creator:"SDN.Leistung Qualitätssicherung",title:"Prüfbericht zur Softwarequalität",description:"Verständliche Erläuterung von Testverfahren Testfällen und Prüfergebnissen",
    styles:{default:{document:{run:{font:FONT,size:22,color:BODY},paragraph:{spacing:{after:135,line:290},widowControl:true}}},
      paragraphStyles:[{id:"Title",name:"Title",basedOn:"Normal",next:"Normal",run:{font:FONT,size:60,bold:true,color:INK},paragraph:{spacing:{after:180}}},
        {id:"Heading1",name:"heading 1",basedOn:"Normal",next:"Normal",quickFormat:true,run:{font:FONT,size:32,bold:true,color:INK},paragraph:{keepNext:true,spacing:{before:340,after:140}}},
        {id:"Heading2",name:"heading 2",basedOn:"Normal",next:"Normal",quickFormat:true,run:{font:FONT,size:25,bold:true,color:INK},paragraph:{keepNext:true,spacing:{before:220,after:140}}}]},
    sections:[{properties:{titlePage:true,page:{size:{width:11906,height:16838},margin:{top:1134,bottom:1134,left:1247,right:1247,header:550,footer:550}}},
      headers:{default:new Header({children:[new Paragraph({children:[text("SDN.Leistung LMG  |  Prüfbericht zur Softwarequalität",{size:18,color:INK})]})]})},
      footers:{default:new Footer({children:[new Paragraph({alignment:AlignmentType.RIGHT,children:[text("Report "+id+"  ·  Seite ",{size:18,color:MUTED}),
        new TextRun({children:[PageNumber.CURRENT],font:FONT,size:18,color:MUTED})]})]})},children}]});
}
module.exports={KAPITEL,baueDokument,ergebnisTabelle,kapitelFazit,zertifikatsSeite,stateLabel};
