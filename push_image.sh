export VERSION=0.4

docker build --no-cache -t chusj/overture-score:$VERSION --target server .;
docker push chusj/overture-score:$VERSION;

docker build --no-cache -t chusj/score-client:$VERSION --target client .;
docker push chusj/score-client:$VERSION;
