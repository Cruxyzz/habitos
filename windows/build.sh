#!/bin/bash
cd "$(dirname "$0")"
mcs -target:winexe -sdk:4.5 -optimize+ -codepage:utf8 -win32icon:habitos.ico -r:System.Windows.Forms.dll -r:System.Drawing.dll -r:System.Core.dll -r:System.Web.Extensions.dll -out:Habitos.exe Model.cs Ui.cs Updater.cs
