import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { Episode } from "@/types/episode";

async function fetchEpisode(id: number): Promise<Episode> {
  const res = await fetch(`/episodes/${id}`);
  if (res.status === 404) {
    throw new Error("NOT_FOUND");
  }
  if (!res.ok) {
    throw new Error(`Failed to load episode (${res.status})`);
  }
  return res.json() as Promise<Episode>;
}

export function EpisodeDetailPage() {
  const { id: idParam } = useParams<{ id: string }>();
  const id = idParam !== undefined ? Number.parseInt(idParam, 10) : Number.NaN;

  const [episode, setEpisode] = useState<Episode | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isFinite(id) || id < 1) {
      setError("Invalid episode id");
      setEpisode(null);
      return;
    }

    let cancelled = false;
    setError(null);
    setEpisode(null);
    fetchEpisode(id)
      .then((data) => {
        if (!cancelled) setEpisode(data);
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          if (e instanceof Error && e.message === "NOT_FOUND") {
            setError("Episode not found.");
          } else {
            setError(e instanceof Error ? e.message : "Failed to load episode");
          }
        }
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  return (
    <div className="mx-auto flex min-h-svh max-w-3xl flex-col gap-8 px-4 py-10">
      <nav>
        <Link
          to="/"
          className="text-muted-foreground hover:text-foreground text-sm underline-offset-4 hover:underline"
        >
          ← All episodes
        </Link>
      </nav>

      <header className="space-y-2">
        <h1 className="font-heading text-3xl font-semibold tracking-tight">
          Episode detail
        </h1>
        <p className="text-muted-foreground text-sm">
          Loaded from{" "}
          <code className="text-foreground">GET /episodes/{"{id}"}</code> (proxied by
          Vite in dev).
        </p>
      </header>

      {error ? (
        <p className="text-destructive text-sm" role="alert">
          {error}
        </p>
      ) : null}

      <section aria-busy={episode === null && !error} aria-label="Episode">
        {episode === null && !error && Number.isFinite(id) && id >= 1 ? (
          <Card>
            <CardHeader>
              <Skeleton className="h-7 w-2/5" />
              <Skeleton className="h-4 w-full" />
            </CardHeader>
            <CardContent className="space-y-2">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-4/5" />
            </CardContent>
          </Card>
        ) : null}

        {episode ? (
          <Card>
            <CardHeader>
              <CardTitle>
                <span className="text-muted-foreground mr-2 font-normal">
                  #{episode.id}
                </span>
                {episode.title}
              </CardTitle>
              {episode.summary ? (
                <CardDescription>{episode.summary}</CardDescription>
              ) : (
                <CardDescription className="italic">No summary</CardDescription>
              )}
            </CardHeader>
          </Card>
        ) : null}
      </section>
    </div>
  );
}
