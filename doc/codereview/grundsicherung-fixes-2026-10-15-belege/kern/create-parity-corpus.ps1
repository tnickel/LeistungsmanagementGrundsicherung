$base = '{"schemaVersion":2,"caseIds":["C1"],"cases":[{"caseId":"C1","verdict":"PASS","findings":[],"reasoning":"geprueft","calculations":[{"blockId":"B1","persons":[{"personId":"p1","values":{"betrag":"563.00"}}],"household":{"summe":"563.00"}}],"checks":[{"checkId":"V1","verdict":"PASS","reasoning":"geprueft"}]}]}'
$req = '[{"caseId":"C1","calculations":[{"blockId":"B1","persons":[{"personId":"p1","values":{"betrag":"563.00"}}],"household":{"summe":"563.00"}}],"checkIds":["V1"],"sourceFindings":[],"missingInputs":[]}]' | ConvertFrom-Json -AsHashtable
$rows = [System.Collections.Generic.List[object]]::new()
function Add-Case([string]$name, [string]$content, [bool]$accepted, [string]$state = 'PASS', [string]$amount = '') {
    $row = [ordered]@{name=$name;content=$content;accepted=$accepted}
    if ($accepted) {$row.state=$state}
    if ($amount) {
        $requirements = @(ConvertFrom-Json -AsHashtable (ConvertTo-Json -InputObject @($req) -Depth 30 -Compress))
        $requirements[0].calculations[0].persons[0].values.betrag = $amount
        $row.requirements = @($requirements)
    }
    $rows.Add($row)
}
Add-Case 'raw-json' $base $true
Add-Case 'ascii-whitespace' (" `t`r`n"+$base+"`r`n ") $true
Add-Case 'markdown-json' ('```json'+"`n"+$base+"`n"+'```') $false
Add-Case 'markdown-plain' ('```'+"`n"+$base+"`n"+'```') $false
Add-Case 'prefix-text' ('Antwort: '+$base) $false
Add-Case 'suffix-text' ($base+' Ende') $false
Add-Case 'second-document' ($base+' {}') $false
Add-Case 'fraction-version' ($base.Replace('"schemaVersion":2','"schemaVersion":2.9')) $false
Add-Case 'rounded-fraction-version' ($base.Replace('"schemaVersion":2','"schemaVersion":2.0000000000000000001')) $false
Add-Case 'decimal-version-two' ($base.Replace('"schemaVersion":2','"schemaVersion":2.0')) $true
Add-Case 'exponent-version-two' ($base.Replace('"schemaVersion":2','"schemaVersion":2e0')) $true
Add-Case 'string-version' ($base.Replace('"schemaVersion":2','"schemaVersion":"2"')) $false
Add-Case 'missing-version' ($base.Replace('"schemaVersion":2,','')) $false
Add-Case 'duplicate-root-key' ($base.Replace('"schemaVersion":2','"schemaVersion":1,"schemaVersion":2')) $false
Add-Case 'escaped-duplicate-key' ($base.Replace('"schemaVersion":2','"schemaVersion":2,"schema\u0056ersion":2')) $false
Add-Case 'duplicate-nested-key' ($base.Replace('"betrag":"563.00"','"betrag":"0.00","betrag":"563.00"')) $false
Add-Case 'duplicate-case-id' ($base.Replace('"caseIds":["C1"]','"caseIds":["C1","C1"]')) $false
Add-Case 'missing-check' ($base.Replace('"checks":[{"checkId":"V1","verdict":"PASS","reasoning":"geprueft"}]','"checks":[]')) $false
Add-Case 'missing-calculation-field' ($base.Replace('"values":{"betrag":"563.00"}','"values":{}')) $false
Add-Case 'additional-calculation-field' ($base.Replace('"betrag":"563.00"','"betrag":"563.00","extra":"0.00"')) $false
Add-Case 'null-value' ($base.Replace('"betrag":"563.00"','"betrag":null')) $false
Add-Case 'boolean-value' ($base.Replace('"betrag":"563.00"','"betrag":true')) $false
Add-Case 'integer-money' ($base.Replace('"betrag":"563.00"','"betrag":563')) $true
Add-Case 'two-decimal-money' ($base.Replace('"betrag":"563.00"','"betrag":563.00')) $true
Add-Case 'three-decimal-money' ($base.Replace('"betrag":"563.00"','"betrag":563.000')) $false
Add-Case 'nonplain-exponent-money' ($base.Replace('"betrag":"563.00"','"betrag":1e2')) $false
Add-Case 'plain-exponent-money' ($base.Replace('"betrag":"563.00"','"betrag":1e-2')) $true 'PASS' '0.01'
Add-Case 'large-exact-numeric-money' ($base.Replace('"betrag":"563.00"','"betrag":9007199254740993')) $true 'PASS' '9007199254740993'
Add-Case 'negative-zero-money' ($base.Replace('"betrag":"563.00"','"betrag":-0.00')) $true 'PASS' '0.00'
Add-Case 'arithmetic-mismatch' ($base.Replace('"betrag":"563.00"','"betrag":"562.00"')) $true 'UNVERIFIED'
Add-Case 'explicit-fail' ($base.Replace('"caseId":"C1","verdict":"PASS","findings":[]','"caseId":"C1","verdict":"FAIL","findings":["Befund"]')) $true 'FAIL'
Add-Case 'explicit-unverified' ($base.Replace('"caseId":"C1","verdict":"PASS","findings":[]','"caseId":"C1","verdict":"UNVERIFIED","findings":["Daten fehlen"]')) $true 'UNVERIFIED'
Add-Case 'contradictory-pass-findings' ($base.Replace('"findings":[]','"findings":["Befund bleibt erhalten"]')) $true 'FAIL'
Add-Case 'workflow-fail' ($base.Replace('"checkId":"V1","verdict":"PASS"','"checkId":"V1","verdict":"FAIL"')) $true 'FAIL'
Add-Case 'workflow-unverified' ($base.Replace('"checkId":"V1","verdict":"PASS"','"checkId":"V1","verdict":"UNVERIFIED"')) $true 'UNVERIFIED'
Add-Case 'fail-without-findings' ($base.Replace('"caseId":"C1","verdict":"PASS"','"caseId":"C1","verdict":"FAIL"')) $false
Add-Case 'blank-reasoning' ($base.Replace('"reasoning":"geprueft"','"reasoning":" "')) $false
Add-Case 'valid-surrogate-pair' ($base.Replace('geprueft','geprueft \ud83d\ude00')) $true
Add-Case 'unpaired-high-surrogate' ($base.Replace('geprueft','\ud800')) $false
Add-Case 'unpaired-low-surrogate' ($base.Replace('geprueft','\udc00')) $false
Add-Case 'exponent-limit' ($base.Replace('"schemaVersion":2','"extra":1e1000,"schemaVersion":2')) $false
Add-Case 'digit-limit' ($base.Replace('"schemaVersion":2','"extra":'+('1'*65)+',"schemaVersion":2')) $false
Add-Case 'depth-limit' ($base.Replace('"schemaVersion":2','"extra":'+('['*128)+'0'+(']'*128)+',"schemaVersion":2')) $false
Add-Case 'java-nonbreaking-space' ($base.Replace('geprueft','\u00a0')) $true
Add-Case 'java-whitespace-separator' ($base.Replace('geprueft','\u001c')) $false
$corpus = [ordered]@{expectedCaseIds=@('C1');requirements=@($req);fixtures=@($rows.ToArray())}
$corpus | ConvertTo-Json -Depth 40 | Set-Content -Encoding utf8 doc/testzertifikat-werkzeug/llm-answer-parity-corpus.json
