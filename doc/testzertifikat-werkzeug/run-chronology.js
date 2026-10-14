// Laufzeitstempel mit Offset sind eindeutig. Lokale Altzeitstempel bleiben
// unbestätigt; bei der Winterzeitumstellung umfasst ihr Intervall beide Zeiten.
const fs=require("fs");
function start(directory,value) {
  if (typeof value === "string" && /(?:Z|[+-]\d\d:\d\d)$/.test(value)) {
    const time=Date.parse(value);if (Number.isFinite(time)) return {earliest:time,latest:time,explicitOffset:true};
  }
  if (typeof value === "string") {
    const match=/^(\d{4})-(\d\d)-(\d\d)T(\d\d):(\d\d):(\d\d)(?:\.(\d{1,9}))?$/.exec(value);
    if (match) {
      const fields=match.slice(1,7).map(Number),ms=Number((match[7]||"").padEnd(3,"0").slice(0,3)),wall=Date.UTC(fields[0],fields[1]-1,...fields.slice(2),ms);
      const offsets=new Set([-172800000,-86400000,0,86400000,172800000].map(delta=>new Date(wall+delta).getTimezoneOffset()));
      const times=[...offsets].map(offset=>wall+offset*60000).filter(time=>{
        const date=new Date(time);return [date.getFullYear(),date.getMonth()+1,date.getDate(),date.getHours(),date.getMinutes(),date.getSeconds()].every((field,i)=>field===fields[i]);
      });
      if (times.length) return {earliest:Math.min(...times),latest:Math.max(...times),explicitOffset:false};
    }
  }
  try {const time=fs.statSync(directory).birthtimeMs;return {earliest:time,latest:time,explicitOffset:false};}
  catch (_) {return {earliest:-Infinity,latest:Infinity,explicitOffset:false};}
}
function candidates(entries) {
  if (!entries.length) return [];
  const newestEarliest=Math.max(...entries.map(entry=>entry.chronology.earliest));
  return entries.filter(entry=>entry.chronology.latest>=newestEarliest);
}
module.exports={start,candidates};
