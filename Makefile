JAVAC = javac
sources = $(wildcard src/Compiler/*.java)
classes = $(addprefix bin/Compiler/, $(notdir $(sources:.java=.class)))
tests = $(wildcard src/test/*.java)
test_classes = $(addprefix bin/test/, $(notdir $(tests:.java=.class)))

all: $(classes)

compiletest: $(test_classes)

clean:
	rm -f $(classes) $(test_classes)

bin/Compiler/%.class: src/Compiler/%.java
	$(JAVAC) -cp src/ -d bin/ $<

bin/test/%.class: src/test/%.java
	$(JAVAC) -cp src/:src/test:bin/:lib/junit-4.12.jar -d bin/test/ $<