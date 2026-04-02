FROM node:22-alpine AS build
WORKDIR /app

# Set npm cache and environment
ENV CI=true NODE_ENV=production
RUN npm cache clean --force

# Copy package files first để tối ưu docker layer cache
COPY package*.json ./
RUN npm ci --only=production

# Copy source code
COPY . .

# Build Angular app
RUN npm run build

# Production stage - Nginx
FROM nginx:1.25-alpine

# Install dumb-init để handle signals properly
RUN apk add --no-cache dumb-init

# Copy nginx config
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy built app from build stage
COPY --from=build /app/dist/angular-fe/browser /usr/share/nginx/html

# Create non-root nginx user and change ownership
RUN chown -R nginx:nginx /usr/share/nginx/html && \
    chmod -R 755 /usr/share/nginx/html

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost/ || exit 1

# Security: run as nginx user
USER nginx
EXPOSE 80

# Use dumb-init to properly handle signals
ENTRYPOINT ["/sbin/dumb-init", "--"]
CMD ["nginx", "-g", "daemon off;"]
