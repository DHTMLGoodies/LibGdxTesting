package com.mygdx.game.customlib.bodyproperties.sprites;

import com.badlogic.gdx.graphics.g2d.PolygonRegion;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.Array;
import com.mygdx.game.customlib.bodyproperties.BodyProperties;
import com.mygdx.game.customlib.util.Geometry;

import net.dermetfan.gdx.math.GeometryUtils;
import net.dermetfan.gdx.physics.box2d.Box2DUtils;


/**
 * Created by alfmagne1 on 09/12/15.
 */
public class PolygonSpriteRenderer {

    private Array<Body> mBodies;
    private EarClippingTriangulator mEarClippingTriangulator;

    public PolygonSpriteRenderer() {
        mBodies = new Array<>();
        mEarClippingTriangulator = new EarClippingTriangulator();
    }

    public void add(Vector2[] textureVertices, Body body, Body parentBody) {


        float[] vertices = Geometry.toFloats(textureVertices);

        BodyProperties parentProperties = BodyProperties.get(parentBody);
        if (parentProperties == null) return;

        BodyProperties properties = BodyProperties.get(body);
        if(properties == null){
            properties = new BodyProperties();
        }

        TextureRegion region = parentProperties.textureRegion;

        if (region == null) {
            body.setUserData(properties);
            return;
        }

        applyProperties(body, properties);

        float minX = parentBody.getPosition().x + Box2DUtils.minX(parentBody);
        float minY = parentBody.getPosition().y + Box2DUtils.minY(parentBody);

        vertices = GeometryUtils.sub(vertices, minX, minY);

        vertices = GeometryUtils.mul(vertices, parentProperties.regionScale, parentProperties.regionScale);

        Vector2 textureMin = new Vector2(GeometryUtils.minX(vertices), GeometryUtils.minY(vertices));
        Vector2 textureMax = new Vector2(GeometryUtils.maxX(vertices), GeometryUtils.maxY(vertices));
        float tileWidth = textureMax.x - textureMin.x;
        float tileHeight = textureMax.y - textureMin.y;

        int y = (int)(region.getRegionHeight() - textureMax.y);

        region = new TextureRegion(region.getTexture(),(int)textureMin.x, y, (int)tileWidth, (int)tileHeight);

        for (int i = 0; i < vertices.length; i += 2) {
            vertices[i] -= textureMin.x;
            vertices[i+1] -= textureMin.y;
        }

        short triangles[] = mEarClippingTriangulator
                .computeTriangles(vertices)
                .toArray();

        properties.regionScale = parentProperties.regionScale;
        properties.polygonRegion = new PolygonRegion(region, vertices, triangles);
        properties.textureRegion = region;

        body.setUserData(properties);

        mBodies.add(body);
    }

    private void applyProperties(Body body, BodyProperties properties) {

        properties.bodyOrigin = new Vector2(
                Box2DUtils.minX(body), Box2DUtils.minY(body)
        );

        properties.bodySize = new Vector2(
                Box2DUtils.maxX(body) - properties.bodyOrigin.x,
                Box2DUtils.maxY(body) - properties.bodyOrigin.y
        );
    }

    public void draw(PolygonSpriteBatch batch) {
        for (Body body : mBodies) {
            draw(batch, body);
        }
    }

    public void draw(PolygonSpriteBatch batch, Body body) {
        /*
   /** Draws the polygon region with the bottom left corner at x,y and stretching the region to cover the given width and height.
	 * The polygon region is offset by originX, originY relative to the origin. Scale specifies the scaling factor by which the
	 * polygon region should be scaled around originX, originY. Rotation specifies the angle of counter clockwise rotation of the
	 * rectangle around originX, originY.

        public void draw (PolygonRegion region, float x, float y, float originX, float originY, float width, float height,
        float scaleX, float scaleY, float rotation) {

        }
         */

        BodyProperties properties = BodyProperties.get(body);
        if (properties != null) {
            PolygonRegion region = properties.polygonRegion;

            Vector2 bodyOrigin = properties.bodyOrigin;
            Vector2 bodyPos = body.getPosition();
            Vector2 bodySize = properties.bodySize;

            float x = bodyPos.x + bodyOrigin.x;
            float y = bodyPos.y + bodyOrigin.y;

            float originX = -bodyOrigin.x;
            float originY = -bodyOrigin.y;
            float width = bodySize.x;
            float height = bodySize.y;
            float scaleX = 1f;
            float scaleY = 1f;
            float rotation = body.getAngle() * MathUtils.radiansToDegrees;

            batch.draw(
                    region, x, y, originX, originY, width, height, scaleX, scaleY, rotation);

        }


    }

    public void destroyBody(Body body) {
        int index = mBodies.indexOf(body, true);
        if (index >= 0) {
            mBodies.removeIndex(index);
        }
    }
}
