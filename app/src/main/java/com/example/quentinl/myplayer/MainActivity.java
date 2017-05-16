package com.example.quentinl.myplayer;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;


import java.io.File;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;

public class MainActivity extends Activity implements View.OnClickListener,ExoPlayer.EventListener,PlaybackControlView.VisibilityListener {


    private Context userAgent = this;
    private SimpleExoPlayerView playerView;
    private DataSource.Factory mediaDataSourceFactory;
    private Handler mainHandler;
    private EventLogger eventLogger;
    private SimpleExoPlayer player;
    private int resumeWindow;
    private long resumePosition;
    private DefaultTrackSelector trackSelector;

    @Override
    protected void onCreate (Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        mediaDataSourceFactory = buildDataSourceFactory();
        clearResumePosition();
        mainHandler= new Handler();
        setContentView(R.layout.activity_main);
        View rootView = findViewById(R.id.root);
        rootView.setOnClickListener(this);
        playerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
        playerView.setControllerVisibilityListener(this);
        playerView.requestFocus();

    }

    private void updateResumePosition() {

    }

    private void clearResumePosition() {
        resumeWindow = C.INDEX_UNSET;
        resumePosition = C.TIME_UNSET;
    }

    @Override
    public void onNewIntent(Intent intent) {
        releasePlayer();
        clearResumePosition();
        setIntent(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if ((Util.SDK_INT <= 23 || player == null)) {
            initializePlayer();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }


    private void initializePlayer() {
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this); // on pourra ajoute + tard la gestion des DRMs
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(new DefaultBandwidthMeter());
        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory); //supporte adaptative Tracks
        player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector);
        player.addListener(this);
        eventLogger = new EventLogger(trackSelector);
        player.addListener(eventLogger);
        player.setMetadataOutput(eventLogger);
        playerView.setPlayer(player);
        player.setPlayWhenReady(true);
        Uri uri= Uri.parse("http://www.youtube.com/api/manifest/dash/id/3aa39fa2cc27967f/source/youtube?as=fmp4_audio_clear,fmp4_sd_hd_clear&sparams=ip,ipbits,expire,source,id,as&ip=0.0.0.0&ipbits=0&expire=19000000000&signature=A2716F75795F5D2AF0E88962FFCD10DB79384F29.84308FF04844498CE6FBCE4731507882B8307798&key=ik0");
        uri=Uri.parse("https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd");

        MediaSource mediaSource = buildMediaSource(uri);
        boolean haveResumePosition = resumeWindow != C.INDEX_UNSET;

        player.prepare(mediaSource,!haveResumePosition,false);

    }

    private MediaSource buildMediaSource(Uri uri) {
        DashMediaSource d= new DashMediaSource(uri,buildDataSourceFactory(),new DefaultDashChunkSource.Factory(mediaDataSourceFactory),mainHandler,eventLogger);
        return d;
    }

    private DataSource.Factory buildDataSourceFactory() {
        DefaultBandwidthMeter bM = new DefaultBandwidthMeter(); //jouer avec ça plus tard je pense
        HttpDataSource.Factory hD = buildHttpDataSourceFactory(bM);
        DefaultDataSourceFactory d = new DefaultDataSourceFactory(userAgent, bM, hD);
        return d;
    }

    private HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return new DefaultHttpDataSourceFactory(userAgent.toString(), bandwidthMeter); // là aussi jouer avec ça
    }

    private void releasePlayer() {
        if (player != null) {
            updateResumePosition();
            player.release();
            player = null;
            eventLogger = null;
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        // Do nothing.
    }

    @Override
    public void onPositionDiscontinuity() {

    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        // Do nothing.
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }


    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

    }


    @Override
    public void onLoadingChanged(boolean isLoading) {
        // Do nothing.
    }

    @Override
    public void onClick(View view) {


    }

    @Override
    public void onVisibilityChange(int visibility) {

    }
}

