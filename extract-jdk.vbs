Set objShell = CreateObject("Shell.Application")
Set objFSO = CreateObject("Scripting.FileSystemObject")

sourceZip = "C:\Users\a.scozzari\Desktop\android-app\jdk-final\jdk.zip"
destinationFolder = "C:\Users\a.scozzari\Desktop\android-app\jdk-final"

WScript.Echo "Extracting " & sourceZip & " to " & destinationFolder

' Create the destination folder if it doesn't exist
If Not objFSO.FolderExists(destinationFolder) Then
    objFSO.CreateFolder(destinationFolder)
End If

' Get the zip file
Set zipFile = objShell.NameSpace(sourceZip)

' Get the destination folder
Set destFolder = objShell.NameSpace(destinationFolder)

' Copy all items from the zip file to the destination folder
Set objFolder = zipFile.Items
destFolder.CopyHere objFolder, 256

WScript.Echo "Extraction complete!"

' Wait a moment
WScript.Sleep 2000

' List the extracted contents
Set folder = objFSO.GetFolder(destinationFolder)
WScript.Echo "Contents of " & destinationFolder & ":"
For Each file In folder.Files
    WScript.Echo "  FILE: " & file.Name
Next
For Each subfolder In folder.SubFolders
    WScript.Echo "  FOLDER: " & subfolder.Name
Next

