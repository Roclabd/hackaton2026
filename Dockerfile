# Сборка в три шага: фронт → бэк (фронт внутри jar) → запуск.
# В рантайме нужен именно JDK, а не JRE: бэкенд компилирует проверяемые программы в памяти через javac.

FROM node:24-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
# vite.config.ts кладёт сборку в ../backend/src/main/resources/web
RUN mkdir -p ../backend/src/main/resources && npm run build

FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY backend/src src
COPY --from=frontend /app/backend/src/main/resources/web src/main/resources/web
COPY corpus /app/corpus
RUN ./mvnw -q -B package

FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY --from=backend /app/backend/target/blueprints.jar blueprints.jar
COPY corpus corpus
ENV LANG=C.UTF-8
EXPOSE 8080
CMD ["java", "-Duser.language=en", "-Duser.country=US", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", "blueprints.jar"]
