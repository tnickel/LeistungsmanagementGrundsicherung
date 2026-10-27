#!/usr/bin/env bash
# Sichere Reproduktion: kein echtes SQL*Plus und keine Datenbankverbindung.
sqlplus(){ printf 'ARG=<%s>\n' "$@"; }
unset ssword
echo 'select 1 from dual;' | sqlplus -S -L review/p$ssword@localhost:1521/FREEPDB1
