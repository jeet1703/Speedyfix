import { useState } from 'react';
import { Icon } from './Icons';

const POSTER = 'https://images.unsplash.com/photo-1615906655593-ad0386982a0f?auto=format&fit=crop&w=1200&h=675&q=70';

/** "How we test every part": a workshop photo, or a play button over it once a video is set (site.video-url). */
export function VideoSection({ videoUrl }: { videoUrl: string }) {
  const [playing, setPlaying] = useState(false);
  const hasVideo = videoUrl.startsWith('https://');
  return (
    <section className="section video" aria-labelledby="video-title">
      <div className="container video__inner">
        <div className="video__copy">
          <p className="eyebrow">Inside SpeedyFix Auto Sales</p>
          <h2 id="video-title" className="h-display">{hasVideo ? 'See how we test every part' : 'How we test every part'}</h2>
          <p>
            {hasVideo
              ? 'A short film from our workshop: how parts are removed, cleaned, tested and graded before they’re listed.'
              : 'Every part is removed, cleaned, tested and graded in our workshop before it’s listed.'}
          </p>
        </div>
        <div className="video__player">
          {playing && hasVideo ? (
            <iframe src={`${videoUrl}?autoplay=1`} title="SpeedyFix Auto Sales workshop video" allow="autoplay; encrypted-media; picture-in-picture" allowFullScreen />
          ) : (
            <>
              <img src={POSTER} alt={hasVideo ? '' : 'Engine parts in the SpeedyFix Auto Sales workshop'} loading="lazy" />
              {hasVideo && (
                <button type="button" className="video__play" aria-label="Play the workshop video" onClick={() => setPlaying(true)}>
                  <Icon name="play" size={34} />
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  );
}
