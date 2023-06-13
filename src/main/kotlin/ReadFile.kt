package sokos.skd.poc

import java.io.File


fun readFileFromOS(fileName: String) = File(fileName).readLines()
