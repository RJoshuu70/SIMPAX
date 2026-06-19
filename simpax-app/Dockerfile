# ============================================================
# Multi-stage build untuk backend SIMPAX
#
# MENGAPA MULTI-STAGE (bukan satu stage saja)?
# Stage "build" memerlukan JDK lengkap + Maven (ukuran besar, ~600MB+)
# hanya untuk proses kompilasi. Setelah jadi .jar, kita TIDAK butuh
# compiler atau Maven lagi untuk MENJALANKAN aplikasi - cukup JRE saja.
# Dengan multi-stage, image FINAL yang benar-benar dipakai (stage
# "runtime") hanya membawa JRE + file .jar, tanpa source code maupun
# toolchain build - hasilnya image jauh lebih kecil dan permukaan
# serangan (attack surface) lebih sempit karena tidak ada compiler/
# source code yang bisa dieksploitasi jika container ter-compromise.
# ============================================================

# ---------- Stage 1: Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy pom.xml DULU sebelum source code - trik caching Docker layer.
# Jika hanya source code yang berubah (pom.xml tetap sama), Docker akan
# reuse cache dependency download dari build sebelumnya, sehingga build
# berikutnya jauh lebih cepat (tidak perlu re-download seluruh dependency
# dari Maven Central setiap kali ada perubahan kode).
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Menjalankan aplikasi sebagai non-root user - prinsip least privilege.
# Jika container ter-compromise, penyerang tidak otomatis mendapat
# privilege root di dalam container.
RUN addgroup -S simpax && adduser -S simpax -G simpax

WORKDIR /app

COPY --from=build /build/target/simpax-app.jar app.jar

RUN chown -R simpax:simpax /app
USER simpax

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
