FROM nginx

LABEL org.opencontainers.image.authors="liugang@wolfcode.cn"

ADD dist/ /usr/share/nginx/html/

EXPOSE 80

CMD ["/bin/sh", "-c", "nginx -g 'daemon off;'"]