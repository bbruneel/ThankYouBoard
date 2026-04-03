---
task: Build a frontend webapp using shadcn ui react on vite server using python backend api's
test_command: "npx ts-node todo.ts list"
---

# Task: CLI Todo App (TypeScript)

Build a simple command-line todo application in TypeScript.

## Requirements

1. Create a GET REST api's on the episode enitity
2. API /episodes/ can list all episodes
3. API /episodes/{id} lists details of episode witd id {id}
4. setup vite server
5. create simple web page that queries all episodes and shows them as the CLI does
6. allow clicking on an episode to show a detailed view of the episode

## Success Criteria

1. [x] Create a GET REST api on the episode enitity in the python codebase. API /episodes/ can list all episodes. API /episodes/{id} lists details of episode witd id {id}
2. [x] set up vite server
3. [x] create all episodes web page running on vite server using tailwind shadcn ui react that queries GET REST api on the episode enitity
4. [x] create single episode webpage for individual episode based on id, they are links from the all episodes web page
5. [x] create playwright tests for all episodes page and single episode page
6. [ ] create PR when everything is done and tests are successfull

## Scaffolding Notes

- Ralph only tracks the checkbox list under `## Success Criteria`
- Keep each checkbox to one outcome you can verify with a command or observable result
- Put manual approval/browser/deploy steps in notes, not in the tracked checklist

---

## Ralph Instructions

1. Work on the next incomplete criterion (marked [ ])
2. Check off completed criteria (change [ ] to [x])
3. Run tests after changes
4. Commit your changes frequently
5. If blocked, record the exact blocker and next command/path in `.ralph/progress.md`
6. When ALL criteria are [x], output: `<ralph>COMPLETE</ralph>`
7. If stuck on the same issue 3+ times, output: `<ralph>GUTTER</ralph>`
