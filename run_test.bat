cd /d/task
docker run --rm -v /d/task/backend:/src -w /src maven:3.9-eclipse-temurin-21 mvn -B -pl app -am test -Dtest="vn.smarttask.project.ProjectMemberAccessIT" > d:\task\final_out.txt 2>&1
echo EXIT_CODE=%errorlevel%