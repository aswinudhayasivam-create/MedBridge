# No defaults for account secrets, destination or expiration: missing configuration fails closed.
server.address=${REVIEW_BIND_ADDRESS:0.0.0.0}
server.port=${PORT:18080}
app.review.enabled=${REVIEW_ENABLED:false}
app.review.purpose=${REVIEW_PURPOSE:}
app.review.transport=${REVIEW_TRANSPORT:hosted-tls}
app.review.database-host=${REVIEW_DB_HOST:}
app.review.expires-at=${REVIEW_EXPIRES_AT:}
app.review.donor-password=${REVIEW_DONOR_PASSWORD:}
app.review.ngo-password=${REVIEW_NGO_PASSWORD:}
app.review.pending-password=${REVIEW_PENDING_PASSWORD:}
app.review.admin-password=${REVIEW_ADMIN_PASSWORD:}
app.allow-registration=false
app.demo-workflows=false
spring.sql.init.mode=never
spring.jpa.hibernate.ddl-auto=none
spring.datasource.url=${REVIEW_DB_URL:}
spring.datasource.username=${REVIEW_DB_USERNAME:}
spring.datasource.password=${REVIEW_DB_PASSWORD:}
spring.datasource.hikari.maximum-pool-size=4
app.jwt.secret=${REVIEW_JWT_SECRET:}
app.jwt.expiry-seconds=1800
app.frontend-url=${REVIEW_FRONTEND_ORIGIN:}

# Hosted TiDB must explicitly select system roots and this exact session guard.
app.review.tls-trust=${REVIEW_TLS_TRUST:}
spring.datasource.hikari.connection-init-sql=${REVIEW_DB_INIT_SQL:}
spring.datasource.hikari.max-lifetime=300000
spring.datasource.hikari.keepalive-time=120000
