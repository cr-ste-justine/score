###############################
# Maven builder
###############################
FROM openjdk:11.0.3-jdk as builder

RUN apt update -y && apt upgrade -y && \
    apt install -y maven

ENV SERVER_JAR_FILE    /score-server.jar
ENV CLIENT_DIST_DIR    /score-client-dist

WORKDIR /srv
COPY . /srv

# Build project
RUN mvn package -DskipTests

# Prepare server jar
RUN cd score-server/target \
    && mv score-server-*-dist.tar.gz score-server.tar.gz \
    && tar zxvf score-server.tar.gz -C /tmp \
    && mv -f /tmp/score-server-*  /tmp/score-server-dist  \
    && cp -f /tmp/score-server-dist/lib/score-server.jar $SERVER_JAR_FILE

# Prepare client dist
RUN cd score-client/target \
	&& mv score-client-*-dist.tar.gz score-client.tar.gz \
    && tar zxvf score-client.tar.gz -C /tmp \
    && mv -f /tmp/score-client-*  /tmp/score-client-dist  \
    && cp -r /tmp/score-client-dist $CLIENT_DIST_DIR \
	&& mkdir -p $CLIENT_DIST_DIR/logs \
	&& touch $CLIENT_DIST_DIR/logs/client.log \
	&& chmod 777 $CLIENT_DIST_DIR/logs/client.log

###############################
# Score Client
###############################
FROM ubuntu:18.04 as client

ENV JDK_DOWNLOAD_URL https://download.java.net/openjdk/jdk11/ri/openjdk-11+28_linux-x64_bin.tar.gz
ENV SCORE_CLIENT_HOME /score-client
ENV PATH /usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$SCORE_CLIENT_HOME/bin

# Update apt, add FUSE support and basic command line tools
RUN \
  apt-get update && \
  apt-get -y upgrade && \
  apt-get install -y libfuse-dev fuse curl wget software-properties-common

# Install Open JDK 11, and remove unused things at runtime 
RUN mkdir /usr/lib/jvm \
	&& cd /usr/lib/jvm \
	&& wget $JDK_DOWNLOAD_URL -O openjdk11.tar.gz \
    && tar zxvf openjdk11.tar.gz \
	&& rm -rf openjdk11.tar.gz \
	&& echo 'PATH=$PATH:/usr/lib/jvm/jdk-11/bin' >> /etc/environment \
	&& echo 'JAVA_HOME=/usr/lib/jvm/jdk-11' >> /etc/environment \
	&& rm -rf /usr/lib/jvm/jdk-11/jmods \
	&& rm -rf /usr/lib/jvm/jdk-11/lib/src.zip \
	&& update-alternatives --install "/usr/bin/java" "java" "/usr/lib/jvm/jdk-11/bin/java" 0 \
	&& update-alternatives --install "/usr/bin/javac" "javac" "/usr/lib/jvm/jdk-11/bin/javac" 0 \
	&& update-alternatives --set java /usr/lib/jvm/jdk-11/bin/java \
	&& update-alternatives --set javac /usr/lib/jvm/jdk-11/bin/javac \
	&& update-alternatives --list java \
	&& update-alternatives --list javac \
	&& java -version \
	&& mkdir /score-client

# Copy client dist from previous docker build staget
COPY --from=builder $CLIENT_DIST_DIR/* $SCORE_CLIENT_HOME/

# Set working directory for convenience with interactive usage
WORKDIR $SCORE_CLIENT_HOME

###############################
# Score Server
###############################
FROM openjdk:11.0.3-jre as server

# Paths
ENV SCORE_HOME /score-server
ENV SCORE_LOGS $SCORE_HOME/logs
ENV JAR_FILE            /score-server.jar

COPY --from=builder $JAR_FILE $JAR_FILE

WORKDIR $SCORE_HOME

CMD mkdir -p  $SCORE_HOME $SCORE_LOGS \
    && java -Dlog.path=$SCORE_LOGS \
    -jar $JAR_FILE \
    --spring.config.location=classpath:/application.yml,classpath:/bootstrap.properties

HEALTHCHECK --interval=10s --timeout=10s --start-period=30s --retries=3 CMD curl -f http://localhost:8080/download/ping