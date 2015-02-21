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

APP=HelloWorld
APPEXENAME=${APP}

APPCSRC=$(shell find ${APPOUTPATH}/classes -name "*.c")
APPCOBJ=$(patsubst %.c,%.o,${APPCSRC})

CC=gcc
CFLAGS=-m32 -O2 -g

all: build

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
	javac -d ${APPOUTPATH}/classes -bootclasspath "" -classpath ${APPCLASSPATH} ${APPSRCPATH}/${APP}.java

xlate: tool app
	@mkdir -p ${APPOUTPATH}/classes
	java -classpath ${CLASSPATH} Linker ${APPCLASSPATH} ${APP} ${APPOUTPATH}

build: xlate
	${MAKE} ${APPOUTPATH}/${APPEXENAME}

${APPOUTPATH}/main.c: xlate

${APPOUTPATH}/libclasses.a: ${APPCOBJ}
	ar cr $@ $^

${APPOUTPATH}/${APPEXENAME}: ${APPOUTPATH}/main.o ${APPOUTPATH}/${NATIVE}.o ${APPOUTPATH}/${JVM}.o ${APPOUTPATH}/libclasses.a
	${CC} ${CFLAGS} -o $@ \
	${APPOUTPATH}/main.o ${APPOUTPATH}/${NATIVE}.o ${APPOUTPATH}/${JVM}.o \
	-L${APPOUTPATH} -lclasses -lm

${APPOUTPATH}/%.o: ${CSRCPATH}/%.c
	${CC} ${CFLAGS} -I ${CSRCPATH} -I ${APPOUTPATH} -c -o $@ $<

%.o: %.c
	${CC} ${CFLAGS} -I ${CSRCPATH} -I ${APPOUTPATH} -c -o $@ $<

clean: cleantool cleanapp cleansdk

cleantool:
	rm -rf ${OUTPATH}

cleanapp:
	rm -rf ${APPOUTPATH}

cleansdk:
	rm -rf ${SDKOUTPATH}

.PHONY: all tool sdk app xlate build clean cleantool cleanapp cleansdk