// Unabhängige centgenaue Kontrollrechnung für die dokumentierten 2026-Eingaben.
// Keine Produktionsberechnung wird aufgerufen. Der Umfang entspricht dem Vertrag.
const cents = value => {
  const match = /^(-?)(\d+)(?:\.(\d{1,2}))?$/.exec(String(value));
  if (!match || match[2].length > 18) throw Error("Ungültiger EUR-Rechenwert");
  return (match[1] ? -1n : 1n) * (BigInt(match[2])*100n+BigInt((match[3] || "").padEnd(2,"0")));
};
const formatted = value => `${value < 0n ? "-" : ""}${(value < 0n ? -value : value)/100n}.${String((value < 0n ? -value : value)%100n).padStart(2,"0")}`;
const min = (a,b)=>a < b ? a : b, max = (a,b)=>a > b ? a : b;
const percentage = (value,num,den=100n) => {
  const n = value*num, abs = n < 0n ? -n : n;
  return (n < 0n ? -1n : 1n)*((abs+den/2n)/den);
};
const rbs = [0n,56300n,50600n,45100n,47100n,39000n,35700n], wwRate=[0n,23n,23n,0n,14n,12n,8n];
function scalar(text,key,required=false) {
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g,"\\$&");
  const matches = [...text.matchAll(new RegExp(`^${escaped}\\s*=\\s*([^\\r\\n]+)`,"gm"))];
  if (matches.length > 1) throw Error(key + " doppelt");
  if (!matches.length) { if (required) throw Error(key + " fehlt");return null; }
  return matches[0][1].trim();
}
function calculate(blockId,text,findings,missing) {
  const persons=[],documented={}; let total=0n,income=0n,protectedAssets=0n,adults=0;
  const rateValue=scalar(text,"kdu_warmwasser_abzug_anteil"),limitValue=scalar(text,"kdu_bg_grenze");
  if ((rateValue===null)!==(limitValue===null)) throw Error("KdU-Parameter unvollständig");
  const rateMatch=/^(\d+)(?:\.(\d{1,12}))?$/.exec(rateValue===null?"0":rateValue);
  if (!rateMatch) throw Error("KdU-Abzugsanteil ungültig");
  const rateDenominator=10n**BigInt((rateMatch[2]||"").length),rate=BigInt(rateMatch[1])*rateDenominator+BigInt(rateMatch[2]||"0"),limit=limitValue===null?0n:cents(limitValue);
  if (rate>rateDenominator || limit<0n) throw Error("KdU-Parameter außerhalb zulässiger Grenzen");
  let remainingKdu=limit>0n?limit:null;
  function compare(key,field,expected) {
    const value = scalar(text,key);
    if (value === null) { missing.push(`${blockId}.${field}: Dokumentwert fehlt`);return; }
    documented[field]=value;
    if (cents(value) !== cents(expected)) findings.push(`${blockId}.${field}: Referenz ${expected} EUR, Dokument ${value} EUR`);
  }
  for (const match of text.matchAll(/^p(\d+)_eingaben: ([^\r\n]+)/gm)) {
    const personId="p"+match[1],input={};
    if (personId !== "p"+(persons.length+1)) throw Error("Personen nicht eindeutig/lückenlos");
    for (const field of match[2].split(", ")) {
      const at = field.indexOf("="); if (at < 0 || Object.hasOwn(input,field.slice(0,at))) throw Error("Eingabefeld doppelt/ungültig");
      input[field.slice(0,at)]=field.slice(at+1);
    }
    const required = key=>{if (!Object.hasOwn(input,key)) throw Error(key+" fehlt");return input[key];};
    const yes=key=>{const value=required(key);if (!["true","false"].includes(value)) throw Error(key+" ungültig");return value === "true";};
    const number=key=>{const value=required(key);if (!/^\d+$/.test(value)) throw Error(key+" ungültig");return Number(value);};
    const money=key=>cents(required(key)),sum=(...keys)=>keys.reduce((total,key)=>total+money(key),0n);
    const level=number("rbs");if (level<1 || level>6) throw Error("RBS außerhalb Referenz 2026");
    const rb=rbs[level],g=yes("merkzeichen_g")?percentage(rb,17n):0n;
    let pregnant=yes("schwanger");const delivery=required("entbindungsmonat"),month=scalar(text,"zahlungsmonat");
    if (pregnant && delivery !== "-") {
      if (!/^\d{4}-\d{2}$/.test(month || "")) missing.push(`${blockId}.${personId}: Zahlungsmonat für Schwangerschaft fehlt`);
      else if (month > delivery) pregnant=false;
    }
    const pregnancy=pregnant?percentage(rb,17n):0n,u7=number("kinder_u7"),u16=number("kinder_u16"),minor=Math.max(u7,u16,number("kinder_minderjaehrig"));
    const preferredRate=Math.max(12*minor,u7>0 || Math.max(u7,u16)>=2 && Math.max(u7,u16)<=3 ? 36 : 0);
    const single=minor === 0 ? 0n : min(percentage(56300n,BigInt(preferredRate)),33780n);
    const decentralized=yes("warmwasser"),ww=decentralized?percentage(rb,wwRate[level],1000n):0n;
    // Altparameter bleibt lesbar; zentrale Warmwasserkosten sind Heizbedarf (§ 35 Abs. 5).
    const kduDeduction=0n,rawKdu=max(sum("kdu_miete","kdu_betrieb","kdu_heizung"),0n);
    const kdu=remainingKdu===null?rawKdu:min(rawKdu,max(remainingKdu,0n));if (remainingKdu!==null) remainingKdu-=kdu;
    const need=rb+min(g+pregnancy+single,rb)+ww+kdu,employed=money("erwerb"),allowance=min(percentage(employed,30n),28150n);
    const deduct=sum("abz_steuern","abz_sv","abz_vers","abz_erwerb"),countable=max(money("rente")+employed-allowance-deduct,0n);
    const values=Object.fromEntries(Object.entries({regelbedarf:rb,mehrbedarf_g:g,mehrbedarf_schwanger:pregnancy,
      mehrbedarf_alleinerziehend:single,mehrbedarf_warmwasser:ww,kdu,gesamtbedarf:need,freibetrag_erwerb:allowance,
      absetzbetaetrag:deduct,anrechenbar:countable,zahlbetrag:need-countable,kdu_warmwasser_abzug:kduDeduction}).map(([key,value])=>[key,formatted(value)]));
    for (const [key,value] of Object.entries(values)) compare(`${personId}_${key}`,`${personId}.${key}`,value);
    persons.push({personId,values});total+=need;income+=countable;protectedAssets+=level<=3?1000000n:50000n;
    if (level<=3) adults++;
  }
  if (!persons.length) throw Error("Personeneingaben fehlen");
  if (!adults) protectedAssets=1000000n;
  const assets=cents(scalar(text,"vermoegen",true)),usable=max(assets-protectedAssets,0n),payout=total-income-usable;
  const household=Object.fromEntries(Object.entries({schonvermoegen:protectedAssets,bg_gesamtbedarf:total,bg_anrechenbar:income,
    bg_verwertbares_vermoegen:usable,bg_zahlbetrag:payout}).map(([key,value])=>[key,formatted(value)]));
  const budget=scalar(text,"budget_start",true);if (budget !== "-") household.budget_rest_hypothetisch=formatted(cents(budget)-payout);
  for (const [key,value] of Object.entries(household)) compare(key,key,value);
  return {blockId,persons,household,documented};
}
function requirement(caseId,text) {
  const calculations=[],missingInputs=[],sourceFindings=[],checkIds=[];
  for (const match of text.matchAll(/^(V\d+): (OK|NIO) ([^\r\n]+)\r?\n([\s\S]*?)(?=^V\d+: |^### |(?![\s\S]))/gm)) {
    const id=match[1],details=match[4];checkIds.push(id);
    if (match[2] === "NIO") sourceFindings.push(id+": dokumentierte NIO-Prüfung");
    const values=/^\s*erwartet = ([^\r\n]+) \| ist = ([^\r\n]+)/m.exec(details);
    if (!values) missingInputs.push(id+": SQL-Soll-/Ist-Nachweis fehlt");
    else {
      let equal;try { equal=decimal(values[1])===decimal(values[2]); } catch (_) { equal=values[1].split("|").includes(values[2]); }
      if (!equal) sourceFindings.push(`${id}: erwartet ${values[1]}, ist ${values[2]}`);
    }
    const global=match[3].includes("Globale Laufverifikation") && details.includes("erwartet = 0 Fehler | ist = 0 Fehler");
    if (!/^\s*sql\s*=\s*.+/m.test(details) || !/^\s*params\s*=\s*.+/m.test(details)
        || !global && /^\s*(?:sql|params)\s*=\s*\?\s*$/m.test(details)) missingInputs.push(id+": SQL oder Parameter nicht vollständig dokumentiert");
  }
  let index=0;
  for (const match of text.matchAll(/^\d{4}-\d\d-\d\d [^\r\n]+ \| [^\r\n]+ -> ([^\r\n]+)/gm)) {
    const id="S"+ ++index;checkIds.push(id);if (!match[1].startsWith("OK") && !match[1].startsWith("ERWARTETER FEHLER")) sourceFindings.push(id+": "+match[1]);
  }
  index=0;
  for (const part of text.split(/(?=^block: )/m)) {
    if (!part.startsWith("block: ")) continue;
    const blockId="B"+ ++index;
    try { calculations.push(calculate(blockId,part,sourceFindings,missingInputs)); }
    catch (error) { missingInputs.push(`${blockId}: unvollständige Recheneingaben (${error.message})`); }
  }
  if (!checkIds.length) missingInputs.push("Keine SQL-Prüfpunkte oder dokumentierten Ablaufschritte");
  return {caseId,calculations,checkIds,missingInputs,sourceFindings};
}
function decimal(value) {
  const match=/^([+-]?)(\d+)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/.exec(value);if (!match) throw Error("Keine Zahl");
  let digits=BigInt(match[2]+(match[3] || "")),scale=(match[3] || "").length-Number(match[4] || 0);
  if (!Number.isSafeInteger(scale) || Math.abs(scale)>1000) throw Error("Zahl zu groß");
  if (digits===0n) return "0";
  while (digits%10n===0n) {digits/=10n;scale--;}
  return (match[1]==="-"?"-":"")+digits+":"+scale;
}
function batchRequirements(text) {
  const matches=[...text.matchAll(/^# Fall-ID: ([^\r\n]+)\r?$/gm)];
  return matches.map((match,index)=>requirement(match[1],text.slice(match.index+match[0].length,index+1<matches.length?matches[index+1].index:text.length).trimStart()));
}
function coverage(req) { return {caseId:req.caseId,calculations:req.calculations.map(block=>({blockId:block.blockId,personIds:block.persons.map(p=>p.personId),householdFields:Object.keys(block.household)})),checkIds:req.checkIds}; }
module.exports={cents,formatted,requirement,batchRequirements,coverage};
