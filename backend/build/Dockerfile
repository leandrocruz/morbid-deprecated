FROM leandrocruz/base-image-java:v11.0

ADD bin  /opt/morbid/service/bin
ADD lib  /opt/morbid/service/lib
ADD conf /opt/morbid/service/conf

RUN mkdir -p /etc/service/morbid-service
ADD run      /etc/service/morbid-service/run

