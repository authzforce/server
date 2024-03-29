# Copyright (C) 2012-2024 Thales.
#
# This file is part of AuthzForce CE.
#
# AuthzForce CE is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# AuthzForce CE is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with AuthzForce CE.  If not, see <http://www.gnu.org/licenses/>.

# Best practices for writing Dockerfiles:
# https://docs.docker.com/engine/userguide/eng-image/dockerfile_best-practices/

# The alternative is to use FROM ubuntu:* then install tomcat ubuntu package and use upstart/sysctl init script but this is not the way to go:
# https://github.com/docker/docker/issues/6800
FROM tomcat:10-jre17-temurin-jammy
LABEL maintainer="AuthzForce Team"
LABEL org.label-schema.schema-version="1.0"
# LABEL org.label-schema.vendor=""
LABEL org.label-schema.name="AuthzForce Server"
# LABEL org.label-schema.description=""

# Proxy configuration (if you are building from behind a proxy)
# Next release of docker 1.9.0 should allow you to configure these by passing build-time arguments
# More info: https://github.com/docker/docker/issues/14634

#ENV http_proxy 'http://user:password@proxy-host:proxy-port'
#ENV https_proxy 'http://user:password@proxy-host:proxy-port'
#ENV HTTP_PROXY 'http://user:password@proxy-host:proxy-port'
#ENV HTTPS_PROXY 'http://user:password@proxy-host:proxy-port'

ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true -Djavax.xml.accessExternalSchema=all -Xms1024m -Xmx1024m -server"

COPY authzforce-ce-server-*.tar.gz /opt/authzforce-ce-server.tar.gz

# Download and install Authzforce Server
# Where there is a command with a pipe, we need to put in between quotes and make it an argument to bash -c command
RUN apt-get update --assume-yes -qq && \
 apt-get install --assume-yes -qq locales-all locales less && \
 rm -rf /var/lib/apt/lists/* 
 
RUN locale-gen en_US en_US.UTF-8 && \
    dpkg-reconfigure locales
ENV LANG en_US.UTF-8 
ENV LANGUAGE en_US:en 
ENV LC_ALL en_US.UTF-8 

RUN cd /opt && \
 tar xvzf authzforce-ce-server.tar.gz && \
 ln -s $(ls -d authzforce-ce-server-*) authzforce-ce-server && \
 mkdir -p /usr/local/tomcat/conf/Catalina/localhost && \
 cp /opt/authzforce-ce-server/conf/context.xml.sample /usr/local/tomcat/conf/Catalina/localhost/authzforce-ce.xml && \
 rm -rf /opt/authzforce-ce-server/data/domains/* && \
 rm -f authzforce-ce-server.tar.gz
 
VOLUME /opt/authzforce-ce-server
VOLUME /usr/local/tomcat/conf
 
CMD ["catalina.sh", "run"]

### Exposed ports
# - App server
EXPOSE 8080
