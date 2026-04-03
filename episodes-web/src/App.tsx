import { BrowserRouter, Route, Routes } from "react-router-dom";

import { EpisodeDetailPage } from "@/pages/EpisodeDetailPage";
import { EpisodesPage } from "@/pages/EpisodesPage";

export default function App() {
  return (
    <BrowserRouter>
      <main>
        <Routes>
          <Route path="/" element={<EpisodesPage />} />
          <Route path="/episodes/:id" element={<EpisodeDetailPage />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}
