SRCPATH=src
OUTPATH=classes
CLASSPATH=../bcel/target/bcel-6.0-SNAPSHOT.jar:${OUTPATH}

SRC=$(shell find ${SRCPATH} -name "*.java")
CLASS=$(patsubst ${SRCPATH}/%.java,${OUTPATH}/%.class,${SRC})

CSRCPATH=csrc
NATIVE=native
JVM=jvm

SDKSRCPATH=sdk/src
SDKOUTPATH=sdk/classes
SDKSRC=$(shell find ${SDKSRCPATH} -name "*.java")
SDKCLASS=$(patsubst ${SDKSRCPATH}/%.java,${SDKOUTPATH}/%.class,${SDKSRC})

APPSRCPATH=app/src
APPOUTPATH=app/build
APPCLASSPATH=${APPOUTPATH}/classes:${SDKOUTPATH}

APP=jbe/DoAll
APPEXENAME=${APP}

APPCSRC=$(shell find ${APPOUTPATH}/classes -name "*.c")

#CC=clang
#CFLAGS=-m32 -O3 -std=c99 -pedantic -Wall -Wno-unused-variable -Wno-unused-parameter -Wno-unused-function -flto
CC=gcc
CFLAGS=-m32 -O3 -std=c99 -pedantic -Wall -Wno-unused-variable -Wno-unused-parameter -Wno-unused-function -Wno-unused-but-set-variable -flto -fwhole-program

all: build doc

tool: ${CLASS}

${OUTPATH}/%.class: ${SRCPATH}/%.java
	@mkdir -p ${OUTPATH}
	javac -d ${OUTPATH} -classpath ${CLASSPATH} -sourcepath ${SRCPATH} $<

sdk: ${SDKCLASS}

${SDKOUTPATH}/%.class: ${SDKSRCPATH}/%.java
	@mkdir -p ${SDKOUTPATH}
	javac -d ${SDKOUTPATH} -bootclasspath "" -classpath ${SDKOUTPATH} -sourcepath ${SDKSRCPATH} $<

app: sdk
	@mkdir -p ${APPOUTPATH}/classes
	javac -d ${APPOUTPATH}/classes -bootclasspath "" -classpath ${APPCLASSPATH} -sourcepath ${APPSRCPATH} ${APPSRCPATH}/${APP}.java

xlate: tool app
	@mkdir -p ${APPOUTPATH}/classes
	java -classpath ${CLASSPATH} fernando.Main ${APPCLASSPATH} ${APP} ${APPOUTPATH}

build: xlate
	${MAKE} ${APPOUTPATH}/${APPEXENAME}

${APPOUTPATH}/${APPEXENAME}: ${APPOUTPATH}/main.c ${APPCSRC} ${CSRCPATH}/${NATIVE}.c ${CSRCPATH}/${JVM}.c
	mkdir -p $(dir ${APPOUTPATH}/${APPEXENAME})
	${CC} ${CFLAGS} -o $@ \
	-I ${CSRCPATH} -I ${APPOUTPATH} \
	$^ -lm -lpthread

run:
	./${APPOUTPATH}/${APPEXENAME}

doc: ${SRC}
	@mkdir -p doc
	javadoc -d doc/javadoc -classpath ${CLASSPATH} -sourcepath ${SRCPATH} fernando

clean: cleantool cleanapp cleansdk cleandoc

cleantool:
	rm -rf ${OUTPATH}

cleanapp:
	rm -rf ${APPOUTPATH}

cleansdk:
	rm -rf ${SDKOUTPATH}

cleandoc:
	rm -rf doc/javadoc

help:
	@echo "Available make targets:"
	@echo "all        build everything (default)"
	@echo "tool       build C code generator"
	@echo "sdk        build Java class library"
	@echo "app        build Java application"
	@echo "xlate      translate Java application to C code"
	@echo "build      compile C code"
	@echo "run        run application"
	@echo "doc        generate documentation"
	@echo "clean      clean everything"
	@echo "cleantool  clean C code generator"
	@echo "cleansdk   clean Java class library"
	@echo "cleanapp   clean Java application and generated C code"
	@echo "cleandoc   clean documentation"
	@echo "help       print this message"

.PHONY: all tool sdk app xlate build run doc clean cleantool cleanapp cleansdk help
