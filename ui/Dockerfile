# Usa un'immagine base di Nginx
FROM nginx:alpine

# Rimuovi la pagina di default di Nginx
RUN rm -rf /usr/share/nginx/html/*
RUN rm -rf /etc/nginx/conf.d/default.conf
RUN rm -rf /etc/nginx/mime.types
# Copia il file HTML della web app nella directory di Nginx
COPY assets/ /usr/share/nginx/html/assets
COPY .nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx_types.conf /etc/nginx/mime.types

# Esponi la porta 3000
EXPOSE 3000

# Comando per avviare Nginx
CMD ["nginx", "-g", "daemon off;"]