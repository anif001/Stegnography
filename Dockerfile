FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /app

COPY . .

RUN mvn clean package

CMD ["java", "-jar", "target/steganography-demo-1.0.0.jar"]