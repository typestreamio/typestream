import React, { useEffect, useRef, useState } from "react";
import "asciinema-player/dist/bundle/asciinema-player.css";

type AsciinemaPlayerProps = {
  src: string;
  cols?: string;
  rows?: string;
  autoPlay?: boolean;
  preload?: boolean;
  loop?: boolean | number;
  startAt?: number | string;
  speed?: number;
  idleTimeLimit?: number;
  theme?: string;
  poster?: string;
  fit?: string;
  terminalFontFamily?: string;
  terminalFontSize?: string;
  controls?: boolean;
};

function AsciinemaPlayer({ src, ...asciinemaOptions }: AsciinemaPlayerProps) {
  const [show, setShow] = useState<boolean>(false);
  const ref = useRef<HTMLDivElement>(null);
  const [player, setPlayer] = useState<typeof import("asciinema-player")>();
  useEffect(() => {
    document.fonts.ready.then(() => {
      import("asciinema-player").then((p) => {
        setPlayer(p);
      });

      setShow(true);
    });
  }, []);
  useEffect(() => {
    const currentRef = ref.current;
    const instance = player?.create(src, currentRef, asciinemaOptions);
    return () => {
      instance?.dispose();
    };
  }, [src, player, asciinemaOptions]);

  return show && <div ref={ref} />;
}

export default AsciinemaPlayer;
