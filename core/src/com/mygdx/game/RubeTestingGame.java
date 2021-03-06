package com.mygdx.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.gushikustudios.rube.RubeScene;
import com.gushikustudios.rube.loader.RubeSceneAsyncLoader;

public class RubeTestingGame extends Game {

    private final AssetManager assetManager = new AssetManager();

	@Override
	public void create () {
        Box2D.init();
        assetManager.setLoader(TiledMap.class, new TmxMapLoader(new InternalFileHandleResolver()));
        assetManager.setLoader(RubeScene.class, new RubeSceneAsyncLoader(new InternalFileHandleResolver()));

        setScreen(new LoadingScreen(this, 3));
	}

    public AssetManager getAssetManager() {
        return assetManager;
    }
}
