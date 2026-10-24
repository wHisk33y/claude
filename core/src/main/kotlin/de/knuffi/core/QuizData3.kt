package de.knuffi.core

// Quiz questions, one per line: level | question | right answer | wrong | wrong | wrong | info

internal const val QUIZ_SPRACHE = """
1|Wer schrieb Märchen wie "Hänsel und Gretel" und "Schneewittchen" auf?|Die Brüder Grimm|Hans Christian Andersen|Wilhelm Busch|Astrid Lindgren|Jacob und Wilhelm Grimm sammelten Märchen, die man sich erzählte, und veröffentlichten sie ab 1812.
1|Wie heißt das Mädchen mit dem roten Umhang, das den Wolf trifft?|Rotkäppchen|Schneewittchen|Aschenputtel|Dornröschen|Im Märchen der Brüder Grimm rettet ein Jäger Rotkäppchen und die Großmutter.
1|Wie viele Zwerge leben bei Schneewittchen?|7|5|6|12|Die sieben Zwerge arbeiten in einem Bergwerk hinter den sieben Bergen.
1|Wer schrieb "Pippi Langstrumpf"?|Astrid Lindgren|Michael Ende|Erich Kästner|Cornelia Funke|Astrid Lindgren aus Schweden schrieb auch "Michel aus Lönneberga" und "Ronja Räubertochter".
1|Wie heißt der Junge, der nie erwachsen werden will?|Peter Pan|Pinocchio|Tom Sawyer|Harry Potter|Peter Pan lebt im Nimmerland und kann fliegen. Sein Gegner ist Käpt'n Hook.
1|Wessen Nase wird länger, wenn er lügt?|Pinocchio|Peter Pan|Rumpelstilzchen|Der Grinch|Pinocchio ist eine Holzpuppe, die der Schnitzer Geppetto gebaut hat.
1|Wie viele Buchstaben hat das Alphabet ohne Ä, Ö, Ü und ß?|26|24|28|30|Davon sind fünf Vokale: A, E, I, O und U.
1|In welchem Märchen schläft eine Prinzessin hundert Jahre lang?|Dornröschen|Rapunzel|Frau Holle|Aschenputtel|Eine Dornenhecke wächst um das Schloss, bis ein Prinz sie wachküsst.
1|Wer lässt ihr langes Haar aus dem Turm herunter?|Rapunzel|Rotkäppchen|Gretel|Frau Holle|"Rapunzel, Rapunzel, lass dein Haar herunter!" ruft die Zauberin im Märchen.
1|Was ist das Gegenteil von "laut"?|Leise|Groß|Schnell|Hell|Wörter mit gegenteiliger Bedeutung nennt man Antonyme.
1|Wie heißt der Bär im "Dschungelbuch"?|Balu|Baghira|Shir Khan|Kaa|Baghira ist der Panther, Shir Khan der Tiger und Kaa die Schlange.
2|Wer schrieb "Die unendliche Geschichte"?|Michael Ende|Erich Kästner|Otfried Preußler|Cornelia Funke|Michael Ende schrieb auch "Momo" und "Jim Knopf und Lukas der Lokomotivführer".
2|Wie heißt die Zauberschule von Harry Potter?|Hogwarts|Durmstrang|Beauxbatons|Askaban|Durmstrang und Beauxbatons sind andere Zauberschulen, Askaban ist ein Gefängnis.
2|Wer schrieb "Emil und die Detektive"?|Erich Kästner|Michael Ende|Otfried Preußler|James Krüss|Erich Kästner schrieb auch "Das doppelte Lottchen" und "Das fliegende Klassenzimmer".
2|Wie nennt man ein Wort, das vorwärts und rückwärts gleich ist, wie "Otto"?|Palindrom|Synonym|Reim|Abkürzung|Weitere Palindrome sind "Anna", "Rentner" und "Reliefpfeiler".
2|Welche Sprache haben die meisten Menschen als Muttersprache?|Chinesisch|Englisch|Spanisch|Deutsch|Mandarin-Chinesisch sprechen über 900 Millionen Menschen als Muttersprache.
2|Wie heißt der Hobbit, der den Ring nach Mordor bringt?|Frodo|Bilbo|Gandalf|Aragorn|"Der Herr der Ringe" schrieb J. R. R. Tolkien.
2|Wer schrieb "Der kleine Prinz"?|Antoine de Saint-Exupéry|Victor Hugo|Jules Verne|Alexandre Dumas|Berühmt ist der Satz: "Man sieht nur mit dem Herzen gut."
2|Wie nennt man Wörter mit gleicher Bedeutung, wie "schnell" und "flink"?|Synonyme|Antonyme|Palindrome|Homonyme|Wörter mit gegenteiliger Bedeutung heißen Antonyme.
2|Wer schrieb "Faust"?|Johann Wolfgang von Goethe|Friedrich Schiller|Heinrich Heine|Thomas Mann|Goethe arbeitete fast sein ganzes Leben lang an diesem Werk.
2|Wer erfand den Detektiv Sherlock Holmes?|Arthur Conan Doyle|Agatha Christie|Edgar Allan Poe|Charles Dickens|Sherlock Holmes wohnt in der Baker Street 221b in London.
2|Wie heißt der berühmte Räuber aus dem Buch von Otfried Preußler?|Räuber Hotzenplotz|Räuber Rumpel|Räuber Kneißl|Räuber Ratzfatz|Kasperl und Seppel jagen ihn, weil er Großmutters Kaffeemühle gestohlen hat.
2|In welcher Sprache sagt man "Hola" zur Begrüßung?|Spanisch|Italienisch|Französisch|Portugiesisch|Auf Italienisch sagt man "Ciao", auf Französisch "Salut" und auf Portugiesisch "Olá".
2|Wer schrieb "Max und Moritz"?|Wilhelm Busch|Heinrich Hoffmann|Erich Kästner|Die Brüder Grimm|Heinrich Hoffmann schrieb dagegen den "Struwwelpeter".
2|Welcher Dichter schrieb "Wilhelm Tell"?|Friedrich Schiller|Johann Wolfgang von Goethe|Gotthold Ephraim Lessing|Heinrich Heine|Wilhelm Tell ist ein Schweizer Held, der einen Apfel vom Kopf seines Sohnes schießen musste.
3|Wer schrieb "Die Verwandlung", in der ein Mann als Käfer aufwacht?|Franz Kafka|Thomas Mann|Hermann Hesse|Bertolt Brecht|Die Hauptfigur heißt Gregor Samsa.
3|Welchen Roman über Napoleons Feldzug nach Russland schrieb Leo Tolstoi?|Krieg und Frieden|Anna Karenina|Schuld und Sühne|Der Idiot|"Schuld und Sühne" und "Der Idiot" stammen von Fjodor Dostojewski.
3|Wer schrieb "Romeo und Julia"?|William Shakespeare|Charles Dickens|Oscar Wilde|Molière|Shakespeare lebte vor über 400 Jahren in England.
3|Aus wie vielen Zeilen besteht ein japanisches Haiku?|3|4|5|14|Die Zeilen haben meist 5, 7 und 5 Silben. Ein Gedicht mit 14 Zeilen ist dagegen ein Sonett.
3|Was bedeutet "carpe diem"?|Nutze den Tag|Ich kam, sah und siegte|Im Wein liegt die Wahrheit|Der Würfel ist gefallen|Das lateinische Zitat stammt vom römischen Dichter Horaz.
3|Welches deutsche Wort wird auch im Englischen benutzt?|Kindergarten|Spielplatz|Schulhof|Klassenzimmer|Friedrich Fröbel gründete 1840 den ersten Kindergarten. Auch "Angst" und "Rucksack" gibt es im Englischen.
3|Wie viele Sprachen werden ungefähr auf der Welt gesprochen?|Etwa 7000|Etwa 200|Etwa 1000|Etwa 50.000|Viele davon sprechen nur wenige tausend Menschen, manche sind vom Aussterben bedroht.
3|Wer schrieb "Die drei Musketiere"?|Alexandre Dumas|Victor Hugo|Jules Verne|Émile Zola|Ihr Motto: "Einer für alle, alle für einen!"
3|Was ist ein Akronym?|Ein Kurzwort aus Anfangsbuchstaben|Ein Gedicht|Ein Fremdwort|Ein Satzzeichen|Beispiele sind "Laser", "UFO" und "Kita".
3|Wer schrieb "In 80 Tagen um die Welt"?|Jules Verne|H. G. Wells|Mark Twain|Karl May|Jules Verne schrieb auch "20.000 Meilen unter dem Meer" und "Reise zum Mittelpunkt der Erde".
3|Wie heißt der Apache in den Büchern von Karl May?|Winnetou|Old Shatterhand|Sitting Bull|Tecumseh|Karl May war selbst nie im Wilden Westen, als er die Bücher schrieb.
3|Was ist ein Anagramm?|Ein Wort aus umgestellten Buchstaben eines anderen|Ein Wort mit drei Silben|Ein Bild aus Buchstaben|Eine Geheimschrift aus Zahlen|Aus "Leib" wird zum Beispiel "Beil" und aus "Regal" wird "Lager".
3|Wer schrieb "Tintenherz"?|Cornelia Funke|Kirsten Boie|Michael Ende|Paul Maar|Paul Maar schrieb dagegen "Das Sams".
3|Wer schrieb "Das Sams"?|Paul Maar|Otfried Preußler|Cornelia Funke|Janosch|Das Sams hat blaue Wunschpunkte im Gesicht und isst am liebsten alles, sogar Holz.
3|Von wem stammt "Oh, wie schön ist Panama"?|Janosch|Paul Maar|Erich Kästner|Otfried Preußler|Darin machen sich der kleine Bär und der kleine Tiger auf die Suche nach Panama.
"""

internal const val QUIZ_FILM = """
1|Wie heißt die Königin mit den Eiskräften in "Die Eiskönigin"?|Elsa|Anna|Arielle|Rapunzel|Ihre Schwester Anna versucht, sie zurückzuholen. Das bekannte Lied heißt "Lass jetzt los".
1|Wie heißt der lustige Schneemann in "Die Eiskönigin"?|Olaf|Sven|Kristoff|Hans|Olaf liebt warme Umarmungen und träumt vom Sommer.
1|Wie heißt der kleine Clownfisch, den sein Vater sucht?|Nemo|Dory|Marlin|Fridolin|Sein Vater heißt Marlin, seine vergessliche Freundin Dory.
1|Welche Farbe hat Shrek?|Grün|Blau|Rot|Gelb|Shrek ist ein Oger, der allein in seinem Sumpf leben möchte. Sein bester Freund ist ein Esel.
1|Wie heißt der gelbe Schwamm aus Bikini Bottom?|SpongeBob|Patrick|Thaddäus|Mr. Krabs|Patrick ist sein bester Freund, ein rosa Seestern.
1|Welche Farbe haben die Minions?|Gelb|Grün|Blau|Rot|Die Minions lieben Bananen und dienen dem Bösewicht Gru.
1|Wie heißt der Klempner mit der roten Mütze aus den Nintendo-Spielen?|Mario|Luigi|Wario|Yoshi|Sein Bruder Luigi trägt Grün. Mario gibt es seit 1981.
1|Welches gelbe Pokémon ist das bekannteste?|Pikachu|Glumanda|Schiggy|Bisasam|Pikachu ist ein Elektro-Pokémon und begleitet Ash auf seinen Abenteuern.
1|Wie heißt der Cowboy in "Toy Story"?|Woody|Buzz Lightyear|Jessie|Rex|Buzz Lightyear ist ein Weltraum-Spielzeug mit dem Spruch "Bis zur Unendlichkeit und noch viel weiter!"
1|Wie heißt der kleine Kobold mit den roten Haaren bei Meister Eder?|Pumuckl|Rumpelstilzchen|Sams|Hui Buh|Pumuckl ist unsichtbar für alle außer Meister Eder und liebt Reime.
1|Welches Tier ist Dumbo?|Ein Elefant|Eine Maus|Ein Bär|Ein Hund|Dumbo kann mit seinen riesigen Ohren fliegen.
1|Was isst Popeye, um stark zu werden?|Spinat|Karotten|Brokkoli|Bohnen|Spinat ist gesund, macht aber nicht ganz so stark wie bei Popeye.
1|Wie heißt die Biene aus der bekannten Zeichentrickserie, deren Freund Willi heißt?|Maja|Flip|Thekla|Puck|Willi schläft am liebsten, und Flip ist ein Grashüpfer.
2|Wie heißt der junge Löwe in "Der König der Löwen"?|Simba|Mufasa|Scar|Nala|Mufasa ist sein Vater, Scar sein böser Onkel. Timon und Pumbaa singen "Hakuna Matata".
2|Wie heißt der rote Rennwagen in "Cars"?|Lightning McQueen|Hook|Doc Hudson|Sally|Hook ist sein bester Freund, ein rostiger Abschleppwagen.
2|In welchem Film kocht eine Ratte in einem Pariser Restaurant?|Ratatouille|Stuart Little|Die Schöne und das Biest|Oben|Die Ratte heißt Remy und steuert den Koch Linguini, indem sie an seinen Haaren zieht.
2|Wie heißt die Eule von Harry Potter?|Hedwig|Errol|Krätze|Seidenschnabel|Krätze ist Rons Ratte, Seidenschnabel ist ein Hippogreif.
2|Welches Spiel besteht aus fallenden Blöcken, die Reihen füllen?|Tetris|Pac-Man|Minecraft|Pong|Tetris wurde 1984 vom russischen Programmierer Alexei Paschitnow erfunden.
2|In welchem Spiel baut man aus Würfeln ganze Welten?|Minecraft|Tetris|Mario Kart|FIFA|Minecraft ist eines der meistverkauften Videospiele aller Zeiten.
2|Wie heißt der Bär aus Peru, der Orangenmarmelade liebt?|Paddington|Balu|Pu der Bär|Yogi|Er wurde nach dem Londoner Bahnhof Paddington benannt, wo ihn die Familie Brown fand.
2|Wie heißt das kleine Schwein, das der beste Freund von Pu dem Bären ist?|Ferkel|Tigger|I-Aah|Rabbit|Tigger ist ein hüpfender Tiger, I-Aah ein trauriger Esel.
2|Wie heißt die kleine Meerjungfrau im Disney-Film von 1989?|Arielle|Elsa|Belle|Jasmin|Arielle tauscht ihre Stimme bei der Meerhexe Ursula gegen Beine.
2|Welcher Superheld wurde von einer Spinne gebissen?|Spider-Man|Batman|Superman|Iron Man|Spider-Man heißt eigentlich Peter Parker und lebt in New York.
2|Von welchem Planeten stammt Superman?|Krypton|Mars|Vulkan|Tatooine|Auf der Erde lebt er als Reporter Clark Kent.
2|Wer ist der Vater von Luke Skywalker?|Darth Vader|Obi-Wan Kenobi|Han Solo|Yoda|"Ich bin dein Vater" ist einer der berühmtesten Sätze der Filmgeschichte.
2|Wie heißt die Stadt, in der Donald Duck wohnt?|Entenhausen|Springfield|Gotham City|Bikini Bottom|Dort wohnt auch sein reicher Onkel Dagobert, der in seinem Geldspeicher badet.
2|Wie heißt der Hund von Obelix?|Idefix|Struppi|Snoopy|Rantanplan|Struppi ist der Hund von Tim, Snoopy gehört Charlie Brown.
2|Welches Getränk macht Asterix unbesiegbar?|Zaubertrank|Met|Milch|Apfelsaft|Der Druide Miraculix braut ihn. Obelix ist als Baby hineingefallen und braucht keinen mehr.
2|Wie heißt die Prinzessin, die Mario immer wieder rettet?|Peach|Daisy|Zelda|Rosalina|Zelda ist die Prinzessin aus einer anderen Nintendo-Spielreihe mit dem Helden Link.
3|Wie heißt das Raumschiff von Han Solo?|Millennium Falke|Enterprise|Todesstern|Galactica|Der Millennium Falke schaffte den Kessel-Flug in weniger als zwölf Parsec.
3|Welcher Disney-Film war 1937 der erste abendfüllende Zeichentrickfilm?|Schneewittchen und die sieben Zwerge|Pinocchio|Bambi|Dumbo|Viele hielten das Projekt damals für verrückt, doch es wurde ein Riesenerfolg.
3|In welcher Stadt leben die Simpsons?|Springfield|Shelbyville|Gotham City|Entenhausen|Die Simpsons laufen seit 1989 und sind die am längsten laufende Zeichentrickserie im Abendprogramm.
3|Wer erschuf 1928 zusammen mit Ub Iwerks die Figur Micky Maus?|Walt Disney|Charles Schulz|Matt Groening|Hergé|Hergé ist der Zeichner von Tim und Struppi, Charles Schulz der von den Peanuts.
3|Wie heißt der Hund von Tim aus "Tim und Struppi"?|Struppi|Idefix|Pluto|Snoopy|Im französischen Original heißt Struppi "Milou".
3|In welchem Land wurde Pokémon erfunden?|Japan|USA|Südkorea|China|Pokémon ist eine Abkürzung für "Pocket Monsters", Taschenmonster.
3|Was bedeutet der Name LEGO?|Spiel gut|Bau mehr|Kleiner Stein|Bunte Welt|LEGO kommt vom dänischen "leg godt". Die Firma stammt aus Billund in Dänemark.
3|Wie heißt die Sendung mit dem blauen Elefanten und der orangefarbenen Maus?|Die Sendung mit der Maus|Löwenzahn|Sesamstraße|Logo!|Die Maus gibt es seit 1971. Die Lach- und Sachgeschichten erklären, wie Dinge funktionieren.
3|Wie hieß der Moderator von "Löwenzahn", der in einem Bauwagen wohnte?|Peter Lustig|Fritz Fuchs|Ernie|Armin Maiwald|Peter Lustig moderierte die Sendung von 1981 bis 2005. Danach übernahm Fritz Fuchs.
3|Welcher Film handelt von einem Haus, das mit Luftballons davonfliegt?|Oben|Wall-E|Alles steht Kopf|Coco|Der alte Carl Fredricksen erfüllt sich so den Traum seiner Frau Ellie.
3|In welchem Pixar-Film leben Gefühle wie Freude und Kummer im Kopf eines Mädchens?|Alles steht Kopf|Oben|Toy Story|Findet Nemo|Das Mädchen heißt Riley. Die Gefühle steuern sie von einer Kommandozentrale aus.
3|Wie heißt der Glücksdrache in "Die unendliche Geschichte"?|Fuchur|Smaug|Grisu|Ohnezahn|Fuchur ist ein weißer Glücksdrache. In der englischen Filmfassung heißt er Falkor.
3|Wie heißt der schwarze Drache in "Drachenzähmen leicht gemacht"?|Ohnezahn|Fuchur|Smaug|Elliot|Ohnezahn ist ein Nachtschatten. Sein Freund ist der junge Wikinger Hicks.
"""

internal const val QUIZ_ESSEN = """
1|Aus welcher Pflanze macht man Pommes frites?|Kartoffel|Karotte|Mais|Reis|Kartoffeln kamen erst vor etwa 400 Jahren aus Südamerika nach Europa.
1|Woraus wird Butter gemacht?|Aus Sahne|Aus Öl|Aus Eiern|Aus Mehl|Schlägt man Sahne sehr lange, trennen sich Fett und Flüssigkeit. Das Fett ist Butter.
1|Welches Obst ist gelb und krumm?|Banane|Apfel|Kirsche|Traube|Bananen wachsen in großen Büscheln mit der Spitze nach oben.
1|Aus welchem Land stammt die Pizza?|Italien|Frankreich|USA|Griechenland|Die Pizza Margherita trägt die Farben der italienischen Flagge: Basilikum, Mozzarella und Tomate.
1|Woraus wird Ketchup hauptsächlich gemacht?|Aus Tomaten|Aus Erdbeeren|Aus Paprika|Aus Kirschen|Dazu kommen Essig, Zucker und Gewürze.
1|Woraus wird Schokolade gemacht?|Aus Kakaobohnen|Aus Kaffeebohnen|Aus Erdnüssen|Aus Datteln|Der Kakaobaum wächst in warmen Ländern nahe am Äquator.
1|Aus welchem Getreide macht man Popcorn?|Mais|Weizen|Reis|Hafer|Im Maiskorn steckt etwas Wasser. Beim Erhitzen wird es zu Dampf, und das Korn platzt auf.
1|Welche Frucht wächst an Palmen und hat innen Wasser?|Kokosnuss|Ananas|Melone|Mango|Das klare Kokoswasser in jungen Kokosnüssen ist ein beliebtes Getränk.
1|Woraus wird Käse gemacht?|Aus Milch|Aus Getreide|Aus Eiern|Aus Obst|Es gibt über 1000 verschiedene Käsesorten auf der Welt.
1|Welches Gemüse ist orange und wächst unter der Erde?|Karotte|Gurke|Paprika|Erbse|Früher gab es Karotten auch in Lila, Weiß und Gelb.
1|Welches Vitamin steckt besonders viel in Zitronen und Orangen?|Vitamin C|Vitamin D|Vitamin K|Vitamin B12|Seefahrer bekamen früher ohne frisches Obst die Krankheit Skorbut. Paprika und Kiwi haben übrigens noch mehr Vitamin C.
2|Aus welchem Land kommt Sushi?|Japan|China|Thailand|Korea|Sushi besteht aus gesäuertem Reis, oft mit rohem Fisch oder Gemüse.
2|Woraus wird Tofu gemacht?|Aus Sojabohnen|Aus Ziegenmilch|Aus Fisch|Aus Reis|Tofu ist reich an Eiweiß und wird in Asien seit über 2000 Jahren gegessen.
2|Welches Land ist für Brüsseler Waffeln bekannt?|Belgien|Schweiz|Österreich|Dänemark|Brüssel ist die Hauptstadt Belgiens. Belgien ist auch für Pommes und Pralinen berühmt.
2|Was ist die Hauptzutat von Guacamole?|Avocado|Tomate|Gurke|Erbse|Guacamole kommt aus Mexiko.
2|Welche Nuss steckt in Nutella?|Haselnuss|Walnuss|Erdnuss|Mandel|Die Nuss-Nougat-Creme wurde in Italien erfunden.
2|Woraus wird Marzipan gemacht?|Aus Mandeln und Zucker|Aus Kartoffeln|Aus Erdnüssen|Aus Kokos|Besonders bekannt ist Marzipan aus Lübeck.
2|Was nennt man in Berlin "Pfannkuchen"?|Den Berliner, ein gefülltes Hefegebäck|Einen Eierkuchen|Eine Pizza|Ein Brötchen|Anderswo heißt das Gebäck Berliner oder Krapfen.
2|Welche Farbe hat das Fruchtfleisch der meisten Kiwis?|Grün|Rot|Blau|Weiß|Es gibt auch gelbe Kiwis, die süßer schmecken.
2|Welches Getränk wird aus Hopfen und Malz gebraut?|Bier|Wein|Apfelsaft|Limonade|Das deutsche Reinheitsgebot von 1516 erlaubte nur Wasser, Gerste und Hopfen.
2|Aus welchem Land kommt die Paella?|Spanien|Italien|Portugal|Mexiko|Die Paella stammt aus der Gegend um Valencia und wird in einer großen flachen Pfanne gekocht.
2|Was sind Rosinen?|Getrocknete Weintrauben|Getrocknete Pflaumen|Kleine Kirschen|Getrocknete Beeren vom Strauch|Getrocknete Pflaumen nennt man Backpflaumen.
2|Was ist Hummus?|Ein Brei aus Kichererbsen|Ein Joghurt|Eine Linsensuppe|Ein Fladenbrot|Hummus wird mit Sesampaste, Zitrone und Knoblauch zubereitet.
2|Welche Zutat lässt Brotteig aufgehen?|Hefe|Salz|Zucker|Butter|Hefe sind winzige Pilze. Sie bilden Gase, die den Teig locker machen.
2|Was sind Spätzle?|Eine schwäbische Teigware|Ein sächsisches Gebäck|Eine Fischsuppe|Ein bayerischer Käse|Spätzle werden traditionell vom Brett ins kochende Wasser geschabt.
3|Wer erfand 1922 die Gummibärchen?|Hans Riegel aus Bonn|Melitta Bentz|Karl Drais|Heinrich Nestlé|Hans Riegel gründete die Firma Haribo. Der Name steht für HAns RIegel BOnn.
3|Was ist Safran?|Ein teures Gewürz aus Krokusblüten|Ein Käse|Ein Fisch|Eine Nuss|Für ein Kilogramm Safran braucht man über 100.000 Blüten, die von Hand gepflückt werden.
3|Welches "Gemüse" ist botanisch eigentlich eine Frucht?|Tomate|Kartoffel|Karotte|Zwiebel|Die Tomate entsteht aus einer Blüte und enthält Samen. Botaniker zählen sie sogar zu den Beeren.
3|Was ist Wasabi?|Eine scharfe grüne Paste aus einer Wurzel|Ein japanischer Tee|Ein Reisgericht|Eine Algenart|Echter Wasabi ist sehr teuer. Oft bekommt man gefärbten Meerrettich.
3|Wie heißt die bayerische Wurst, die man traditionell vor dem Mittagsläuten isst?|Weißwurst|Bratwurst|Bockwurst|Wiener|Die Haut der Weißwurst isst man nicht mit. Man "zuzelt" sie heraus.
3|Welches Land isst pro Kopf besonders viel Schokolade?|Schweiz|China|Brasilien|Japan|Schweizerinnen und Schweizer essen über zehn Kilogramm Schokolade pro Jahr.
3|Woraus wird Tee gemacht?|Aus den Blättern des Teestrauchs|Aus Kaffeebohnen|Aus Baumrinde|Aus Kakaoschalen|Schwarzer und grüner Tee stammen von derselben Pflanze, sie werden nur anders verarbeitet.
3|Aus welchem Land stammt der Kaffee ursprünglich?|Äthiopien|Brasilien|Italien|Kolumbien|Der Legende nach bemerkte ein Hirte, dass seine Ziegen nach dem Fressen roter Kaffeekirschen munter herumsprangen.
3|Was ist Couscous?|Grieß aus Hartweizen|Eine Bohnenart|Ein Reisgericht|Eine Kartoffelsorte|Couscous ist besonders in Nordafrika beliebt.
3|Wie heißt das Dessert aus Löffelbiskuit, Mascarpone und Kaffee?|Tiramisu|Panna cotta|Crème brûlée|Zabaione|Tiramisu bedeutet auf Italienisch "zieh mich hoch".
3|Welcher Stoff macht Chilischoten scharf?|Capsaicin|Koffein|Zucker|Vitamin C|Capsaicin reizt Schmerzrezeptoren im Mund. Deshalb fühlt sich Schärfe heiß an.
3|Welche Frucht nennt man auch "Königin der Früchte" in Südostasien?|Mangostane|Banane|Apfel|Kiwi|Die Durian, eine stark riechende Frucht, nennt man dort dagegen den "König der Früchte".
3|Welches Gewürz wird aus der Rinde eines Baumes gewonnen?|Zimt|Pfeffer|Muskat|Vanille|Die Rinde rollt sich beim Trocknen zu Stangen ein.
3|Woher kommt die Vanille ursprünglich?|Aus Mexiko|Aus China|Aus Indien|Aus Italien|Vanille ist die Schote einer Orchidee. Sie ist nach Safran eines der teuersten Gewürze.
"""

internal const val QUIZ_DEUTSCHLAND = """
1|Wie heißt die Hauptstadt von Deutschland?|Berlin|München|Hamburg|Bonn|Berlin ist mit fast 4 Millionen Einwohnern auch die größte Stadt Deutschlands.
1|Welche Farben hat die deutsche Flagge?|Schwarz, Rot, Gold|Rot, Weiß, Blau|Blau, Gelb|Grün, Weiß, Rot|Die Farben stammen aus der Zeit der Freiheitskriege vor rund 200 Jahren.
1|Wie heißt das berühmte Tor in Berlin?|Brandenburger Tor|Holstentor|Siegestor|Porta Nigra|Oben auf dem Tor steht die Quadriga, ein Wagen mit vier Pferden.
1|Wie heißt der höchste Berg Deutschlands?|Zugspitze|Brocken|Watzmann|Feldberg|Die Zugspitze ist 2962 Meter hoch und liegt an der Grenze zu Österreich.
1|In welcher Stadt findet das Oktoberfest statt?|München|Berlin|Köln|Hamburg|Das Oktoberfest beginnt meistens schon im September.
1|Welcher Fluss fließt durch Köln?|Rhein|Elbe|Donau|Main|Der Rhein ist einer der meistbefahrenen Flüsse der Welt.
1|Wie viele Bundesländer hat Deutschland?|16|10|12|20|Drei davon sind Stadtstaaten: Berlin, Hamburg und Bremen.
1|Welches Tier ist das Wappentier Deutschlands?|Adler|Löwe|Bär|Hirsch|Der schwarze Bundesadler hat rote Krallen und einen roten Schnabel.
2|Wie viele Nachbarländer hat Deutschland?|9|5|7|12|Dänemark, Polen, Tschechien, Österreich, die Schweiz, Frankreich, Luxemburg, Belgien und die Niederlande.
2|Welches ist das flächenmäßig größte Bundesland?|Bayern|Niedersachsen|Nordrhein-Westfalen|Baden-Württemberg|Bayern macht fast ein Fünftel der Fläche Deutschlands aus.
2|In welchem Bundesland leben die meisten Menschen?|Nordrhein-Westfalen|Bayern|Baden-Württemberg|Niedersachsen|In Nordrhein-Westfalen leben rund 18 Millionen Menschen.
2|Welche ist die größte Insel Deutschlands?|Rügen|Sylt|Fehmarn|Usedom|Rügen liegt in der Ostsee und ist berühmt für seine weißen Kreidefelsen.
2|An welchem Tag feiert Deutschland den Tag der Deutschen Einheit?|3. Oktober|9. November|1. Mai|17. Juni|Am 3. Oktober 1990 wurde Deutschland wiedervereinigt.
2|Welche Stadt ist berühmt für ihren Dom mit zwei über 150 Meter hohen Türmen?|Köln|Bonn|Düsseldorf|Mainz|Der Bau des Kölner Doms dauerte mit Pausen über 600 Jahre, von 1248 bis 1880.
2|Welches Gebirge liegt ganz im Süden Deutschlands?|Die Alpen|Der Harz|Das Erzgebirge|Der Taunus|Die Alpen erstrecken sich über acht Länder, darunter die Schweiz, Österreich, Italien und Frankreich.
2|Wie lautet der Anfang der deutschen Nationalhymne?|Einigkeit und Recht und Freiheit|Freude, schöner Götterfunken|Alle meine Entchen|Ich hab mein Herz in Heidelberg verloren|Der Text stammt von August Heinrich Hoffmann von Fallersleben, die Melodie von Joseph Haydn.
2|In der Nähe welcher Stadt steht Schloss Neuschwanstein?|Füssen|Dresden|Berlin|Heidelberg|König Ludwig II. ließ das Märchenschloss bauen. Es soll das Vorbild für das Disney-Schloss gewesen sein.
2|Welcher Fluss fließt durch Hamburg?|Elbe|Rhein|Weser|Main|Der Hamburger Hafen ist einer der größten Häfen Europas.
2|Welche Stadt wird "Elbflorenz" genannt?|Dresden|Hamburg|Magdeburg|Leipzig|Wegen ihrer prächtigen Bauten wie der Frauenkirche und dem Zwinger erinnert sie an Florenz.
2|Wie heißt das deutsche Parlament?|Bundestag|Senat|Kongress|Unterhaus|Der Bundestag tagt im Reichstagsgebäude in Berlin mit der gläsernen Kuppel.
2|Welche Autofirma hat ihren Sitz in Wolfsburg?|Volkswagen|BMW|Mercedes-Benz|Audi|BMW sitzt in München, Mercedes-Benz in Stuttgart und Audi in Ingolstadt.
3|In welchem Bundesland liegt Heidelberg?|Baden-Württemberg|Hessen|Bayern|Rheinland-Pfalz|Heidelberg hat die älteste Universität Deutschlands, gegründet 1386.
3|Wer wählt den Bundespräsidenten?|Die Bundesversammlung|Das Volk direkt|Der Bundesrat allein|Die Ministerpräsidenten|Die Bundesversammlung besteht aus den Mitgliedern des Bundestages und genauso vielen Vertretern der Länder.
3|Wie heißt die Verfassung der Bundesrepublik Deutschland?|Grundgesetz|Bürgerliches Gesetzbuch|Magna Carta|Bundesordnung|Artikel 1 beginnt mit dem Satz: "Die Würde des Menschen ist unantastbar."
3|Welcher ist der größte See, der ganz in Deutschland liegt?|Müritz|Bodensee|Chiemsee|Starnberger See|Der Bodensee ist größer, liegt aber auch in Österreich und der Schweiz.
3|In welcher Stadt sitzt das Bundesverfassungsgericht?|Karlsruhe|Berlin|Bonn|Leipzig|Das Gericht wacht darüber, dass Gesetze mit dem Grundgesetz übereinstimmen.
3|Welche Stadt war die Hauptstadt der DDR?|Ost-Berlin|Leipzig|Dresden|Rostock|Die DDR bestand von 1949 bis 1990.
3|Auf welchem Berg tanzen der Sage nach in der Walpurgisnacht die Hexen?|Brocken|Zugspitze|Feldberg|Wasserkuppe|Der Brocken ist mit 1141 Metern der höchste Berg im Harz.
3|Woher kommt die Kuckucksuhr traditionell?|Aus dem Schwarzwald|Aus Bayern|Aus Thüringen|Aus Sachsen|Die ersten Kuckucksuhren entstanden dort vor etwa 300 Jahren.
3|Wie viele Menschen leben ungefähr in Deutschland?|Etwa 84 Millionen|Etwa 40 Millionen|Etwa 120 Millionen|Etwa 200 Millionen|Deutschland ist damit das Land mit den meisten Einwohnern in der Europäischen Union.
3|Welche Figuren aus einem Grimm-Märchen sind das Wahrzeichen Bremens?|Die Bremer Stadtmusikanten|Hänsel und Gretel|Die sieben Zwerge|Der Froschkönig|Esel, Hund, Katze und Hahn stehen als Bronzefigur am Rathaus. Wer die Vorderbeine des Esels berührt, soll Glück haben.
3|Wie nennen die Hamburger ihre Hauptkirche St. Michaelis?|Michel|Mike|Micha|Michi|Der Turm des Michels ist 132 Meter hoch.
3|Welche deutsche Stadt hat besonders viele Brücken, mehr als Venedig?|Hamburg|Köln|Frankfurt am Main|Nürnberg|Hamburg hat rund 2500 Brücken.
3|Welches Bundesland ist das kleinste?|Bremen|Saarland|Hamburg|Berlin|Bremen besteht aus den Städten Bremen und Bremerhaven.
3|In welcher Stadt steht die Porta Nigra, ein römisches Stadttor?|Trier|Köln|Augsburg|Regensburg|Trier gilt als älteste Stadt Deutschlands und wurde von den Römern gegründet.
3|Welches Bundesland hat die Hauptstadt Schwerin?|Mecklenburg-Vorpommern|Brandenburg|Schleswig-Holstein|Niedersachsen|Das Schweriner Schloss steht auf einer Insel im Schweriner See.
3|Welche Hauptstadt gehört zu Sachsen?|Dresden|Leipzig|Chemnitz|Magdeburg|Leipzig ist zwar die größte Stadt Sachsens, Hauptstadt ist aber Dresden.
2|Welches Bundesland hat die Hauptstadt München?|Bayern|Sachsen|Thüringen|Baden-Württemberg|Auch Sachsen und Thüringen nennen sich Freistaat.
3|Welcher Kanal verbindet Nordsee und Ostsee?|Nord-Ostsee-Kanal|Mittellandkanal|Main-Donau-Kanal|Elbe-Lübeck-Kanal|Er ist einer der meistbefahrenen künstlichen Wasserwege der Welt.
"""

internal const val QUIZ_ZAHLEN = """
1|Wie viele Tage hat eine Woche?|7|5|6|10|Die Wochentage sind teilweise nach Göttern und Himmelskörpern benannt, der Sonntag nach der Sonne.
1|Wie viele Minuten hat eine Stunde?|60|100|30|50|Dass wir in Sechzigern rechnen, geht auf die Babylonier zurück.
1|Wie viele Tage hat ein normales Jahr?|365|360|366|400|Alle vier Jahre gibt es ein Schaltjahr mit 366 Tagen, dann hat der Februar 29 Tage.
1|Wie viele Monate hat ein Jahr?|12|10|11|13|Der Juli ist nach Julius Cäsar benannt, der August nach Kaiser Augustus.
1|Welcher Monat hat die wenigsten Tage?|Februar|April|Juni|November|Der Februar hat 28 Tage, im Schaltjahr 29.
1|Wie viele Ecken hat ein Dreieck?|3|4|5|6|Die Winkel in jedem Dreieck ergeben zusammen immer 180 Grad.
1|Wie viele Seiten hat ein Spielwürfel?|6|4|8|12|Gegenüberliegende Seiten eines Würfels ergeben zusammen immer 7.
1|Wie viel ist ein Dutzend?|12|10|20|6|Früher kaufte man Eier oft im Dutzend.
1|Wie viele Stunden hat ein Tag?|24|12|20|48|Weil sich die Erde in 24 Stunden einmal um sich selbst dreht.
1|Wie viele Zentimeter hat ein Meter?|100|10|1000|50|Ein Zentimeter ist ein Hundertstel Meter. "Zenti" kommt vom lateinischen "centum", hundert.
2|Wie viele Sekunden hat eine Stunde?|3600|600|1000|6000|60 Minuten mal 60 Sekunden ergibt 3600 Sekunden.
2|Wie viele Nullen hat eine Million?|6|3|9|12|Eine Million ist tausend mal tausend.
2|Welche Zahl steht in römischen Ziffern für 10?|X|V|L|C|V ist 5, L ist 50 und C ist 100.
2|Welche Zahl schreibt man römisch als L?|50|5|100|1000|Die Römer kannten keine Null.
2|Wie heißt das höchste Gebäude der Welt?|Burj Khalifa|Empire State Building|Eiffelturm|Berliner Fernsehturm|Der Burj Khalifa in Dubai ist 828 Meter hoch.
2|Wie lang ist der Äquator ungefähr?|40.000 Kilometer|4.000 Kilometer|400.000 Kilometer|10.000 Kilometer|Mit einem Flugzeug bräuchte man ohne Pause etwa zwei Tage einmal herum.
2|Wie viele Ecken hat ein Sechseck?|6|5|8|7|Bienenwaben bestehen aus vielen Sechsecken.
2|Was ist 7 mal 8?|56|54|64|48|Eselsbrücke: 5, 6, 7, 8, also 56 = 7 mal 8.
2|Wie viele Grad hat ein Vollkreis?|360|180|90|100|Ein rechter Winkel hat 90 Grad, also ein Viertel davon.
2|Wie viele Grad hat ein rechter Winkel?|90|45|180|60|Rechte Winkel findest du zum Beispiel an den Ecken eines Buches.
2|Was ist die Hälfte von 1000?|500|250|100|50|Ein Viertel von 1000 wären 250.
2|Wie viele Beine haben drei Hühner und zwei Hunde zusammen?|14|10|12|16|Drei Hühner haben 6 Beine, zwei Hunde haben 8 Beine, zusammen 14.
3|Wie lautet Pi auf zwei Nachkommastellen?|3,14|2,72|1,41|3,41|Pi ist das Verhältnis von Umfang zu Durchmesser eines Kreises. Seine Nachkommastellen hören nie auf.
3|Was ist eine Primzahl?|Eine Zahl, die nur durch 1 und sich selbst teilbar ist|Eine gerade Zahl|Eine Zahl mit Komma|Eine sehr große Zahl|Die kleinsten Primzahlen sind 2, 3, 5, 7 und 11.
3|Welche ist die kleinste Primzahl?|2|1|3|0|Die 2 ist auch die einzige gerade Primzahl. Die 1 zählt nicht als Primzahl.
3|Wie viele Nullen hat eine Milliarde?|9|6|12|8|Eine Milliarde sind tausend Millionen.
3|Wie heißt das Ergebnis einer Multiplikation?|Produkt|Summe|Differenz|Quotient|Die Summe ist das Ergebnis einer Addition, der Quotient das einer Division.
3|Wie viele Karten hat ein Pokerkartenspiel ohne Joker?|52|32|48|54|Vier Farben mit je 13 Karten. Ein Skatspiel hat dagegen 32 Karten.
3|Wie viele Sekunden hat ein Tag?|86.400|3.600|10.000|1.000.000|24 Stunden mal 3600 Sekunden ergibt 86.400 Sekunden.
3|Welche Zahl folgt in der Reihe 1, 1, 2, 3, 5, 8?|13|11|10|16|Jede Zahl ist die Summe der beiden davor. Diese Folge heißt Fibonacci-Folge.
3|Wie schreibt man 2024 in römischen Ziffern?|MMXXIV|MMXXXIV|MCMXXIV|MMXIV|M steht für 1000, X für 10, und IV ist 4.
3|Was ist die Quadratwurzel aus 144?|12|14|11|13|12 mal 12 ergibt 144.
3|Wie viele Menschen leben ungefähr auf der Erde?|Über 8 Milliarden|Etwa 1 Milliarde|Etwa 3 Milliarden|Etwa 20 Milliarden|Die Marke von 8 Milliarden wurde im Jahr 2022 überschritten.
3|Wie viele Minuten hat ein Tag?|1440|1000|2400|3600|24 mal 60 ergibt 1440 Minuten.
3|Wie viel sind 15 Prozent von 200?|30|15|20|45|10 Prozent von 200 sind 20, 5 Prozent sind 10, zusammen 30.
3|Welcher Monat hat 31 Tage?|Juli|April|Juni|September|Die Faustregel mit den Fingerknöcheln hilft: Jeder Knöchel ist ein Monat mit 31 Tagen.
3|Wie viele Kanten hat ein Würfel?|12|6|8|24|Ein Würfel hat 6 Flächen, 8 Ecken und 12 Kanten.
3|Was ist größer: ein Drittel oder ein Viertel?|Ein Drittel|Ein Viertel|Beide sind gleich|Kommt darauf an|Teilt man eine Pizza in drei statt vier Teile, ist jedes Stück größer.
3|Wie viele Liter passen ungefähr in eine Badewanne?|Etwa 150|Etwa 15|Etwa 1500|Etwa 50|Duschen braucht deshalb oft viel weniger Wasser als Baden.
3|Welches Tier hat das schnellste Herz unter diesen?|Spitzmaus|Elefant|Wal|Pferd|Das Herz einer Spitzmaus kann über 1000-mal in der Minute schlagen, das eines Blauwals nur wenige Male.
"""
