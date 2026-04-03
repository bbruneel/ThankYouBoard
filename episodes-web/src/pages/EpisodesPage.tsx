import { useEffect, useState } from "react";

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { Episode } from "@/types/episode";

async function fetchEpisodes(): Promise<Episode[]> {
  const res = await fetch("/episodes/");
  if (!res.ok) {
    throw new Error(`Failed to load episodes (${res.status})`);
  }
  return res.json() as Promise<Episode[]>;
}

export function EpisodesPage() {
  const [episodes, setEpisodes] = useState<Episode[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    fetchEpisodes()
      .then((data) => {
        if (!cancelled) setEpisodes(data);
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : "Failed to load episodes");
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="mx-auto flex min-h-svh max-w-3xl flex-col gap-8 px-4 py-10">
      <header className="space-y-2">
        <h1 className="font-heading text-3xl font-semibold tracking-tight">
          Episodes
        </h1>
        <p className="text-muted-foreground text-sm">
          Loaded from the FastAPI <code className="text-foreground">GET /episodes/</code>{" "}
          endpoint (proxied by Vite in dev).
        </p>
      </header>

      {error ? (
        <p className="text-destructive text-sm" role="alert">
          {error}
        </p>
      ) : null}

      <section
        className="grid gap-4 sm:grid-cols-1"
        aria-busy={episodes === null && !error}
        aria-label="Episode list"
      >
        {episodes === null && !error
          ? Array.from({ length: 3 }).map((_, i) => (
              <Card key={i}>
                <CardHeader>
                  <Skeleton className="h-5 w-2/5" />
                  <Skeleton className="h-4 w-full" />
                </CardHeader>
                <CardContent className="space-y-2">
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-4 w-4/5" />
                </CardContent>
              </Card>
            ))
          : null}

        {episodes?.map((ep) => (
          <Card key={ep.id}>
            <CardHeader>
              <CardTitle>
                <span className="text-muted-foreground mr-2 font-normal">
                  #{ep.id}
                </span>
                {ep.title}
              </CardTitle>
              {ep.summary ? (
                <CardDescription>{ep.summary}</CardDescription>
              ) : (
                <CardDescription className="italic">No summary</CardDescription>
              )}
            </CardHeader>
          </Card>
        ))}
      </section>
    </div>
  );
}
