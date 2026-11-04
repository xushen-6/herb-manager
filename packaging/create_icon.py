from PIL import Image,ImageDraw
from pathlib import Path
import sys
base=Path(sys.argv[1]);im=Image.new('RGBA',(1024,1024),(0,0,0,0));d=ImageDraw.Draw(im)
d.rounded_rectangle((12,12,1012,1012),radius=225,fill='#174f3c')
d.rounded_rectangle((40,40,984,984),radius=204,outline='#65946c',width=9)
d.line([(350,793),(459,603),(568,407),(692,245)],fill='#f3e9be',width=30)
d.polygon([(451,606),(275,564),(220,442),(244,296),(394,344),(487,465)],fill='#9fbd7d')
d.polygon([(501,530),(610,525),(777,440),(809,283),(667,303),(572,390)],fill='#dae6ad')
d.line([(281,360),(365,478),(451,606)],fill='#eaf0ca',width=12)
d.line([(501,530),(636,425),(755,332)],fill='#7e9e62',width=12)
d.ellipse((661,687,777,803),outline='#d6b86c',width=13)
d.line([(685,773),(751,715)],fill='#d6b86c',width=9)
im=im.resize((256,256),Image.Resampling.LANCZOS)
im.save(base/'src/main/resources/assets/herb-icon.png')
im.save(base/'packaging/herb.ico',sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
print('HERB_ICON_CREATED')
