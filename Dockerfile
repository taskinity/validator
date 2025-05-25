FROM openjdk:11-jre-slim

WORKDIR /app
COPY *.groovy ./
COPY .env.example .env
COPY data/ data/

RUN apt-get update && apt-get install -y groovy
EXPOSE 8080 9090

CMD ["groovy", "run.groovy"]
