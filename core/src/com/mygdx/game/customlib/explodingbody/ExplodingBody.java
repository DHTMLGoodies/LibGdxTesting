package com.mygdx.game.customlib.explodingbody;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.g2d.PolygonSprite;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.GeometryUtils;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.RayCastCallback;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.gushikustudios.rube.loader.serializers.utils.RubeImage;
import com.mygdx.game.SpriteGenerator;
import com.mygdx.game.customlib.bodyproperties.BodyProperties;
import com.mygdx.game.customlib.bodyproperties.sprites.BodySpriteRenderer;
import com.mygdx.game.customlib.bodyproperties.sprites.PolygonSpriteRenderer;


/**
 * Exploding body
 * Created by alfmagne1 on 06/12/15.
 */
public class ExplodingBody implements RayCastCallback {

    private static final int CHUNKS = 3;

    private Array<Body> affectedBodies = new Array<>();

    private Array<Body> affectedByLaser;
    private Array<Vector2> entryPoint = new Array<>();

    private World mWorld;

    private Vector2 explosionCenter;
    private float explosionRadius;

    private Array<Body> mSlices;
    private Array<Body> mBodies;

    private Vector2 bodyMinOriginal;

    private EarClippingTriangulator mEarClippingTriangulator;

    private BodySpriteRenderer mBodySpriteRenderer;
    private PolygonSpriteRenderer mPolygonSpriteRenderer;


    public ExplodingBody(World world) {
        mWorld = world;
        mSlices = new Array<>();
        mBodies = new Array<>();
        mEarClippingTriangulator = new EarClippingTriangulator();
        mBodySpriteRenderer = new BodySpriteRenderer();
        mPolygonSpriteRenderer = new PolygonSpriteRenderer();
    }

    public void add(RubeImage rubeImage, AssetManager assetManager) {
        Sprite sprite = SpriteGenerator.generateSprite(assetManager, rubeImage);
        add(rubeImage.body, sprite);
        mBodySpriteRenderer.add(rubeImage, assetManager);
    }

    public void add(Body body, Sprite sprite) {
        BodyProperties properties;
        if (body.getUserData() != null) {
            properties = (BodyProperties) body.getUserData();
        } else {
            properties = new BodyProperties();
        }

        properties.sprite = sprite;
        body.setUserData(properties);

        mBodies.add(body);
    }

    public boolean canExplode(Body body) {
        return mBodies.contains(body, true);
    }

    public void explode(Body body) {

        Gdx.app.log("fixtures", "fixtures: " + body.getFixtureList().size);

        if (body.getFixtureList().size == 0) return;

        explosionCenter = body.getPosition();
        explosionRadius = 5;
        float worldScale = 1;
        affectedBodies.add(body);



        for (int i = 0; i < CHUNKS; i++) {
            float cutAngle = MathUtils.random() * MathUtils.PI2;
            float offset = 10;

            Vector2 p1 = new Vector2((explosionCenter.x + (float) i / 1000 - offset * MathUtils.cos(cutAngle)) / worldScale, (explosionCenter.y - offset * MathUtils.sin(cutAngle)) / worldScale);
            Vector2 p2 = new Vector2((explosionCenter.x + (offset * MathUtils.cos(cutAngle))) / worldScale, (explosionCenter.y + (offset * MathUtils.sin(cutAngle))) / worldScale);

            affectedByLaser = new Array<>();
            entryPoint = new Array<>();

            mWorld.rayCast(this, p1, p2);
            mWorld.rayCast(this, p2, p1);
        }
    }

    @Override
    public float reportRayFixture(Fixture fixture, Vector2 point, Vector2 normal, float fraction) {

        Body affectedBody = fixture.getBody();

        if (affectedBodies.contains(affectedBody, true) && fixture.getShape() instanceof PolygonShape) {

            float angle = affectedBody.getAngle();
            BodyProperties properties = (BodyProperties) affectedBody.getUserData();
            properties.angleOnExplosion = angle;
            affectedBody.setTransform(affectedBody.getPosition(), 0);

            PolygonShape affectedPolygon = (PolygonShape) fixture.getShape();
            int fixtureIndex = affectedByLaser.indexOf(affectedBody, true);

            if (fixtureIndex == -1) {
                affectedByLaser.add(affectedBody);
                entryPoint.add(new Vector2(point.x, point.y));
            } else {

                Vector2 entry = entryPoint.get(fixtureIndex);
                // entry.x = 13f; entry.y = 7f;
                // point.x = 16f; point.y = 8f;
                splitBody(affectedBody, affectedPolygon, entry, point);
            }
        }
        return 1;
    }

    private void splitBody(Body affectedBody, PolygonShape affectedPolygon, Vector2 entry, Vector2 exit) {
        Vector2 rayCenter = new Vector2((exit.x + entry.x) / 2, (exit.y + entry.y) / 2);
        float rayAngle = MathUtils.atan2(entry.y - exit.y, entry.x - exit.x);

        Vector2[] polyVertices = new Vector2[affectedPolygon.getVertexCount()];

        int vertexCount = affectedPolygon.getVertexCount();
        Vector2 currentVertex = new Vector2();
        for (int i = 0; i < vertexCount; i++) {
            affectedPolygon.getVertex(i, currentVertex);
            polyVertices[i] = new Vector2(currentVertex.x, currentVertex.y);
        }

        Array<Vector2> newPolyVertices1 = new Array<>();
        Array<Vector2> newPolyVertices2 = new Array<>();

        int currentPoly = 0;
        boolean cutPlaced1 = false;
        boolean cutPlaced2 = false;

        for (Vector2 polyVertice : polyVertices) {

            Vector2 worldPoint = affectedBody.getWorldPoint(polyVertice);

            float cutAngle = MathUtils.atan2(worldPoint.y - rayCenter.y, worldPoint.x - rayCenter.x) - rayAngle;
            if (cutAngle < MathUtils.PI * -1) {
                cutAngle += MathUtils.PI2;
            }

            if (cutAngle > 0 && cutAngle <= MathUtils.PI) {
                if (currentPoly == 2) {
                    cutPlaced1 = true;
                    newPolyVertices1.add(new Vector2(exit.x, exit.y));
                    newPolyVertices1.add(new Vector2(entry.x, entry.y));
                }
                newPolyVertices1.add(new Vector2(worldPoint.x, worldPoint.y));
                currentPoly = 1;
            } else {
                if (currentPoly == 1) {
                    cutPlaced2 = true;
                    newPolyVertices2.add(new Vector2(entry.x, entry.y));
                    newPolyVertices2.add(new Vector2(exit.x, exit.y));
                }
                newPolyVertices2.add(new Vector2(worldPoint.x, worldPoint.y));
                currentPoly = 2;

            }
        }

        if (!cutPlaced1) {
            newPolyVertices1.add(new Vector2(exit.x, exit.y));
            newPolyVertices1.add(new Vector2(entry.x, entry.y));
        }
        if (!cutPlaced2) {
            newPolyVertices2.add(new Vector2(entry.x, entry.y));
            newPolyVertices2.add(new Vector2(exit.x, exit.y));
        }

        Vector2[] vertices1 = new Vector2[newPolyVertices1.size];
        for (int i = 0; i < newPolyVertices1.size; i++) {
            vertices1[i] = newPolyVertices1.get(i);
        }
        Vector2[] vertices2 = new Vector2[newPolyVertices2.size];
        for (int i = 0; i < newPolyVertices2.size; i++) {
            vertices2[i] = newPolyVertices2.get(i);
        }

        createSlice(vertices1, affectedBody);
        createSlice(vertices2, affectedBody);
        destroyBody(affectedBody);

    }

    private void destroyBody(Body body) {

        mBodySpriteRenderer.destroy(body);
        mPolygonSpriteRenderer.destroyBody(body);

        int index = mSlices.indexOf(body, true);
        if (index >= 0) {
            mSlices.removeIndex(index);
        }
        index = mBodies.indexOf(body, true);
        if (index >= 0) {
            mBodies.removeIndex(index);
        }
        mWorld.destroyBody(body);


    }

    private float debugAngle = 0;

    public void update(float delta) {

        debugAngle += 1;
        debugAngle = debugAngle % 360f;

        BodyProperties properties;

        for (Body body : mSlices) {

            Object userData = body.getUserData();
            if (userData != null) {
                properties = (BodyProperties) userData;

                if (properties.polygonSprite != null) {

                    PolygonSprite sprite = properties.polygonSprite;

                    Vector2 origin = new Vector2(properties.polygonSpriteOrigin);
                    origin.rotate(body.getAngle() * MathUtils.radiansToDegrees);

                    Vector2 position = new Vector2(body.getPosition());

                    sprite.setPosition(
                            position.x + origin.x - properties.polygonSpriteOrigin.x,
                            position.y + origin.y - properties.polygonSpriteOrigin.y
                    );
                    sprite.setRotation(body.getAngle() * MathUtils.radiansToDegrees);
                }
            }
        }
    }

    public void draw(SpriteBatch batch) {
        mBodySpriteRenderer.draw(batch);
    }

    public void draw(PolygonSpriteBatch spriteBatch) {
        mPolygonSpriteRenderer.draw(spriteBatch);
        /*
        BodyProperties properties;

        for (Body body : mSlices) {
            if (body.getUserData() != null) {
                properties = (BodyProperties) body.getUserData();
                if (properties.polygonSprite != null) {
                    properties.polygonSprite.draw(spriteBatch);
                }
            }
        }
        */
    }


    private float[] vectorToArrayToFloats(Vector2[] vertices) {
        float[] floats = new float[vertices.length * 2];
        int index = 0;
        for (Vector2 vertex : vertices) {
            floats[index++] = vertex.x;
            floats[index++] = vertex.y;
        }
        return floats;
    }

    private void createSlice(Vector2[] vertices, Body fromBody) {

        if (getArea(vertices) > 0.05f) {

            BodyProperties properties = (BodyProperties) fromBody.getUserData();

            Vector2 centre = findCentroid(vertices);

            BodyDef sliceBodyDef = new BodyDef();
            sliceBodyDef.type = BodyDef.BodyType.DynamicBody;
            Body sliceBody = mWorld.createBody(sliceBodyDef);

            PolygonShape slicePoly = new PolygonShape();

            Vector2[] textureVertices = new Vector2[vertices.length];

            int index = 0;
            for (Vector2 vertex : vertices) {
                textureVertices[index++] = new Vector2(vertex.x, vertex.y);
                vertex.sub(centre);
            }

            slicePoly.set(vertices);
            sliceBody.createFixture(slicePoly, 1);

            if (properties.angleOnExplosion != 0) {
                sliceBody.setTransform(centre, properties.angleOnExplosion);
            } else {
                sliceBody.setTransform(centre, properties.angleOnExplosion);
            }

            mPolygonSpriteRenderer.add(textureVertices, sliceBody, fromBody);

            BodyProperties tmp = BodyProperties.get(sliceBody);
            if(tmp == null){
                Gdx.app.log("body properties", "PROPS IS NULL===================");
            }

            float distX = centre.x - explosionCenter.x;

            if (distX < 0) {
                if (distX < -explosionRadius) {
                    distX = 0;
                } else {
                    distX = -explosionRadius - distX;
                }
            } else {
                if (distX > explosionRadius) {
                    distX = 0;
                } else {
                    distX = 50 - distX;
                }
            }
            float distY = centre.y - explosionCenter.y;
            if (distY < 0) {
                if (distY < -explosionRadius) {
                    distY = 0;
                } else {
                    distY = -explosionRadius - distY;
                }
            } else {
                if (distY > explosionRadius) {
                    distY = 0;
                } else {
                    distY = explosionRadius - distY;
                }
            }
            distX *= 0.25;
            distY *= 0.25;
            //sliceBody.setLinearVelocity(new Vector2(distX, distY));

            addAffectedBody(sliceBody, fromBody);
            slicePoly.dispose();




        }
    }

    private void addAffectedBody(Body body, Body fromBody) {

        affectedBodies.add(body);
    }

    public void dispose() {

    }

    private float getArea(Vector2[] vs) {
        if (vs.length < 3) return 0;
        float[] vertices = new float[vs.length * 2];
        int index = 0;
        for (Vector2 v : vs) {
            vertices[index++] = v.x;
            vertices[index++] = v.y;
        }

        return GeometryUtils.polygonArea(vertices, 0, vertices.length);

    }

    private Vector2 findCentroid(Vector2[] vs) {
        if (vs.length < 3) {
            return new Vector2(0, 0);
        }
        float[] vertices = new float[vs.length * 2];
        int index = 0;

        for (Vector2 v : vs) {
            vertices[index++] = v.x;
            vertices[index++] = v.y;
        }
        Vector2 centre = new Vector2();
        GeometryUtils.polygonCentroid(vertices, 0, vertices.length, centre);
        return centre;
    }
}
