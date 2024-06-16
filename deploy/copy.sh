#!/bin/bash

# 前端
if [ -f "frontend-server/src/main/resources/static/index.html" ]; then
  echo 'copying frontend-server to build path...'
  mkdir -p deploy/frontend-server/build/dist/
  cp -r frontend-server/src/main/resources/static/* deploy/frontend-server/build/dist/
fi

# 网关
if [ -f "shop-parent/api-gateway/target/classes/bootstrap.yml" ]; then
  echo 'copying api-gateway to build path...'
  mkdir -p deploy/api-gateway/build/target/
  cp shop-parent/api-gateway/target/*.jar deploy/api-gateway/build/target/
  cp shop-parent/api-gateway/target/classes/bootstrap.yml deploy/api-gateway/build/target/
fi

# 用户
if [ -f "shop-parent/shop-uaa/target/classes/bootstrap.yml" ]; then
  echo 'copying shop-uaa to build path...'
  mkdir -p deploy/shop-uaa/build/target/
  cp shop-parent/shop-uaa/target/*.jar deploy/shop-uaa/build/target/
  cp shop-parent/shop-uaa/target/classes/bootstrap.yml deploy/shop-uaa/build/target/
fi

# 商品
if [ -f "shop-parent/shop-provider/flashsale-server/target/classes/bootstrap.yml" ]; then
  echo 'copying flashsale-server to build path...'
  mkdir -p deploy/flashsale-server/build/target/
  cp shop-parent/shop-provider/flashsale-server/target/*.jar deploy/flashsale-server/build/target/
  cp shop-parent/shop-provider/flashsale-server/target/classes/bootstrap.yml deploy/flashsale-server/build/target/
fi

# 秒杀
if [ -f "shop-parent/shop-provider/product-server/target/classes/bootstrap.yml" ]; then
  echo 'copying product-server to build path...'
  mkdir -p deploy/product-server/build/target/
  cp shop-parent/shop-provider/product-server/target/*.jar deploy/product-server/build/target/
  cp shop-parent/shop-provider/product-server/target/classes/bootstrap.yml deploy/product-server/build/target/
fi
