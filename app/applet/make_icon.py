import subprocess

cmd = [
    "convert", "-size", "512x512", "xc:#121826",
    "-fill", "white", "-draw", "roundrectangle 64,64 448,400 40,40",
    "-fill", "#0D47A1", "-draw", "roundrectangle 80,80 432,384 32,32",
    "-fill", "#26A69A", "-draw", "rectangle 96,112 416,192",
    "-fill", "white", "-draw", "rectangle 96,208 250,336", "-draw", "rectangle 266,208 416,336",
    "-fill", "#FFC107", "-draw", "circle 384,320 384,272",
    "-fill", "white", "-draw", "polygon 368,300 368,340 380,340 396,352 396,288 380,300",
    "-fill", "#00E676", "-draw", "rectangle 112,390 128,430",
    "-fill", "#00B0FF", "-draw", "rectangle 144,370 160,450",
    "-fill", "#7C4DFF", "-draw", "rectangle 176,350 192,470",
    "-fill", "#E040FB", "-draw", "rectangle 208,330 224,490",
    "-fill", "#7C4DFF", "-draw", "rectangle 240,360 256,460",
    "-fill", "#00B0FF", "-draw", "rectangle 272,380 288,440",
    "-fill", "#FFD600", "-draw", "rectangle 304,340 320,480",
    "-fill", "#FF9100", "-draw", "rectangle 336,370 352,450",
    "-fill", "#FFEA00", "-draw", "rectangle 368,400 384,420",
    "/tmp/icon.png"
]
subprocess.run(cmd, check=True)
print("Icon generated successfully")
