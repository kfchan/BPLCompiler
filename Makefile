JAVAC = javac
sources = $(wildcard src/Compiler/*.java)
classes = $(notdir $(sources:.java=.class))

all: $(classes)

clean:
	rm -f bin/Compiler/*.class

%.class: src/Compiler/%.java
	$(JAVAC) -cp src -d bin/ $<