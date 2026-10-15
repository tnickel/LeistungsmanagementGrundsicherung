// Modellantworten folgen denselben JSON-Grenzen wie de.allgemein.lmg.web.Json.
// JSON.parse allein verwirft doppelte Schlüssel und Rundungsreste der Version.
function parseModelAnswer(content) {
  if (typeof content !== "string") throw Error("Modellantwort ist kein vollständiges JSON");
  const result=JSON.parse(content);
  let position=0,versionToken=null;
  const whitespace=()=>{while (position<content.length && /[ \t\r\n]/.test(content[position])) position++;};
  const expect=character=>{if (content[position++]!==character) throw Error("Ungültiges JSON");};
  function string() {
    const start=position;expect('"');
    while (position<content.length) {
      const character=content[position++];
      if (character==='\\') position++;
      else if (character==='"') {
        const value=JSON.parse(content.slice(start,position));
        for (let i=0;i<value.length;i++) {
          const unit=value.charCodeAt(i);
          if (unit>=0xd800 && unit<=0xdbff) {
            const next=value.charCodeAt(++i);
            if (!(next>=0xdc00 && next<=0xdfff)) throw Error("Ungepaartes UTF-16-Surrogate");
          } else if (unit>=0xdc00 && unit<=0xdfff) throw Error("Ungepaartes UTF-16-Surrogate");
        }
        return value;
      }
    }
    throw Error("Unvollständige JSON-Zeichenfolge");
  }
  function decimal(token) {
    const match=/^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(token);
    const coefficient=(match[2]+(match[3] || "")).replace(/^0+(?=\d)/,"");
    const scale=(match[3] || "").length-Number(match[4] || 0),adjusted=coefficient.length-scale-1;
    // BigDecimal.toString benutzt bei negativer Scale oder sehr kleinen Werten
    // Exponentialnotation; diese ist kein EUR-Zwischenwert des Java-Vertrags.
    if (scale<0 || adjusted < -6) throw Error("Ungültiger EUR-Rechenwert");
    const sign=match[1] && /[1-9]/.test(coefficient)?"-":"";
    const digits=coefficient.padStart(scale+1,"0");
    const normalized=sign+(scale?digits.slice(0,-scale)+"."+digits.slice(-scale):digits);
    if (!/^-?\d{1,18}(?:\.\d{1,2})?$/.test(normalized)) throw Error("Ungültiger EUR-Rechenwert");
    return normalized;
  }
  function monetary(path) {
    return path[0]==="cases" && path[2]==="calculations" && (path[4]==="household" && path.length===6
      || path[4]==="persons" && path[6]==="values" && path.length===8);
  }
  function value(depth,path=[]) {
    whitespace();
    const character=content[position];
    if (character==='{' || character==='[') {
      if (++depth>128) throw Error("JSON zu tief verschachtelt");
      position++;whitespace();
      const object=character==='{',end=object?'}':']',keys=new Set();
      if (content[position]===end) {position++;return;}
      let index=0;
      while (true) {
        whitespace();let key=null;
        if (object) {
          key=string();
          if (keys.has(key)) throw Error("Doppelter Objektschlüssel");
          keys.add(key);whitespace();expect(':');
        }
        whitespace();const start=position;
        value(depth,[...path,object?key:index++]);
        if (object && depth===1 && key==="schemaVersion") versionToken=content.slice(start,position);
        whitespace();
        if (content[position]===end) {position++;return;}
        expect(',');
      }
    }
    if (character==='"') {string();return;}
    for (const literal of ["true","false","null"]) if (content.startsWith(literal,position)) {position+=literal.length;return;}
    const match=/^-?(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?/.exec(content.slice(position));
    if (!match) throw Error("Ungültiger JSON-Wert");
    if (match[1].length+(match[2] || "").length>64 || Math.abs(Number(match[3] || 0))>999) throw Error("JSON-Zahl außerhalb erlaubter Grenzen");
    if (monetary(path)) {
      // Kein binäres Number-Runden: Auch mehr als 2^53 ganze EUR bleiben exakt.
      const parent=path.slice(0,-1).reduce((node,key)=>node[key],result);
      parent[path.at(-1)]=decimal(match[0]);
    }
    position+=match[0].length;
  }
  value(0);whitespace();
  if (position!==content.length) throw Error("Zusatztext nach JSON");
  // Der native Number-Wert allein würde z. B. 2.0000000000000000001 runden.
  const match=/^(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(versionToken || "");
  let exactVersion=false;
  if (match) {
    const coefficient=BigInt(match[1]+(match[2] || "")),power=Number(match[3] || 0)-(match[2] || "").length;
    exactVersion=power>=0?coefficient*10n**BigInt(power)===2n:coefficient===2n*10n**BigInt(-power);
  }
  if (!exactVersion) throw Error("Schema-Version muss exakt 2 sein");
  return result;
}
module.exports={parseModelAnswer};
