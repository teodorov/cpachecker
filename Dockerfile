FROM openjdk:13-jdk-slim

WORKDIR /usr/app
ENV PATH="/etc/ant/bin:${PATH}"
ENV ANT_HOME="/etc/ant"

RUN apt-get update && apt-get install -y \
  graphviz \
  libgomp1 \
  python3 \
  wget \
  git

# RUN apt-get update && (apt-get install -y ant || true)
RUN cd /tmp; \
    wget http://apache.mirror.digionline.de/ant/binaries/apache-ant-1.10.7-bin.tar.gz && \
    tar -C /etc/ -xzvf apache-ant-1.10.7-bin.tar.gz apache-ant-1.10.7 && \
    mv /etc/apache-ant-1.10.7 /etc/ant && \
    cd ${ANT_HOME} && \
    ant -f fetch.xml -Ddest=system && \
    rm -rf /tmp/apache-ant*

COPY build /usr/app/build
COPY lib /usr/app/lib
COPY build*.xml /usr/app/
RUN ant resolve-dependencies

COPY . /usr/app

# RUN ant build