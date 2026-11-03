package dev.colonyjournal.analysis;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.IAssignmentModuleView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import dev.colonyjournal.graph.BuildingGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** A snapshot of the colony: which building types work, how they are linked, and what is missing. */
public final class ColonyReport {
   public enum Status {
      ABSENT,
      UNBUILT,
      NO_WORKER,
      PARTIAL,
      OK;

      public boolean working() {
         return this == OK || this == PARTIAL;
      }
   }

   public enum Severity {
      ERROR,
      WARN,
      INFO
   }

   public record TypeInfo(String id, Component name, int count, int built, int workers, int slots, Status status) {
   }

   public record Link(BuildingGraph.Edge edge, boolean broken, Status producer, Status consumer) {
   }

   public record Issue(Severity severity, Component text, String focus) {
   }

   public record Row(BlockPos pos, String typeId, Component name, int level, int maxLevel, int workers, int slots, int requests, Status status) {
   }

   public final String colonyName;
   public final Map<String, TypeInfo> types = new LinkedHashMap<>();
   public final List<Link> links = new ArrayList<>();
   public final List<Issue> issues = new ArrayList<>();
   public final List<Row> rows = new ArrayList<>();
   public int citizens;
   public int citizenLimit;

   private ColonyReport(String colonyName) {
      this.colonyName = colonyName;
   }

   public static Component nameOf(String id) {
      BuildingEntry entry = IMinecoloniesAPI.getInstance().getBuildingRegistry().get(ResourceLocation.fromNamespaceAndPath("minecolonies", id));
      return entry == null ? Component.literal(id) : Component.translatable(entry.getTranslationKey());
   }

   public Status statusOf(String id) {
      TypeInfo info = this.types.get(id);
      return info == null ? Status.ABSENT : info.status();
   }

   private static String idOf(IBuildingView view) {
      BuildingEntry type = view.getBuildingType();
      return type == null || type.getRegistryName() == null ? "unknown" : type.getRegistryName().getPath();
   }

   private static int slotsOf(IBuildingView view) {
      int slots = 0;

      for (IAssignmentModuleView module : view.getModuleViews(IAssignmentModuleView.class)) {
         if (module.getJobEntry() != null) {
            slots += module.getMaxInhabitants();
         }
      }

      return slots;
   }

   private static int workersOf(IBuildingView view) {
      int workers = 0;

      for (IAssignmentModuleView module : view.getModuleViews(IAssignmentModuleView.class)) {
         if (module.getJobEntry() != null) {
            workers += module.getAssignedCitizens().size();
         }
      }

      return workers;
   }

   private static List<IRequest<?>> requestsOf(IColonyView colony, IBuildingView view) {
      List<IRequest<?>> out = new ArrayList<>(view.getOpenRequestsOfBuilding());

      try {
         for (Collection<IToken<?>> tokens : view.getOpenRequestsByCitizen().values()) {
            for (IToken<?> token : tokens) {
               try {
                  IRequest<?> request = colony.getRequestManager().getRequestForToken(token);
                  if (request != null && !out.contains(request)) {
                     out.add(request);
                  }
               } catch (IllegalArgumentException ignored) {
                  // the request was resolved between the sync of the building and of the request manager
               }
            }
         }
      } catch (RuntimeException ignored) {
         // a half-synced view must never break the journal
      }

      return out;
   }

   private static boolean open(IRequest<?> request) {
      RequestState state = request.getState();
      return state == RequestState.CREATED
         || state == RequestState.REPORTED
         || state == RequestState.ASSIGNING
         || state == RequestState.ASSIGNED
         || state == RequestState.IN_PROGRESS
         || state == RequestState.FOLLOWUP_IN_PROGRESS;
   }

   public static ColonyReport build(IColonyView colony) {
      ColonyReport report = new ColonyReport(colony.getName());
      report.citizens = colony.getCitizenCount();
      report.citizenLimit = colony.getCitizenCountLimit();
      Map<String, List<IBuildingView>> byType = new HashMap<>();
      Map<BlockPos, List<IRequest<?>>> requests = new HashMap<>();

      for (IBuildingView view : colony.getClientBuildingManager().getBuildings().values()) {
         byType.computeIfAbsent(idOf(view), k -> new ArrayList<>()).add(view);
         requests.put(view.getPosition(), requestsOf(colony, view));
      }

      byType.forEach((id, views) -> report.types.put(id, summarize(id, views)));
      for (String id : BuildingGraph.NODES.keySet()) {
         report.types.computeIfAbsent(id, k -> new TypeInfo(id, nameOf(id), 0, 0, 0, 0, Status.ABSENT));
      }

      for (String id : BuildingGraph.BASICS) {
         report.types.computeIfAbsent(id, k -> new TypeInfo(id, nameOf(id), 0, 0, 0, 0, Status.ABSENT));
      }

      for (List<IBuildingView> views : byType.values()) {
         for (IBuildingView view : views) {
            String id = idOf(view);
            int slots = slotsOf(view);
            int workers = workersOf(view);
            List<IRequest<?>> open = requests.getOrDefault(view.getPosition(), List.of());
            report.rows.add(
               new Row(
                  view.getPosition(),
                  id,
                  view.getBuildingDisplayName().isEmpty() ? nameOf(id) : Component.literal(view.getBuildingDisplayName()),
                  view.getBuildingLevel(),
                  view.getBuildingMaxLevel(),
                  workers,
                  slots,
                  (int)open.stream().filter(ColonyReport::open).count(),
                  statusOf(view.getBuildingLevel(), slots, workers)
               )
            );
         }
      }

      report.rows.sort(Comparator.comparing((Row r) -> r.typeId()).thenComparing(r -> r.pos()));
      report.collectLinks();
      report.collectBasics();
      report.collectWorkerIssues();
      report.collectRequestIssues(requests, byType);
      report.issues.sort(Comparator.comparing(Issue::severity));
      return report;
   }

   private static Status statusOf(int level, int slots, int workers) {
      if (level <= 0) {
         return Status.UNBUILT;
      } else if (slots <= 0) {
         return Status.OK;
      } else if (workers <= 0) {
         return Status.NO_WORKER;
      } else {
         return workers < slots ? Status.PARTIAL : Status.OK;
      }
   }

   private static TypeInfo summarize(String id, List<IBuildingView> views) {
      int built = 0;
      int workers = 0;
      int slots = 0;

      for (IBuildingView view : views) {
         if (view.getBuildingLevel() > 0) {
            built++;
            workers += workersOf(view);
            slots += slotsOf(view);
         }
      }

      Status status = built == 0 ? Status.UNBUILT : statusOf(1, slots, workers);
      return new TypeInfo(id, nameOf(id), views.size(), built, workers, slots, status);
   }

   private static Component reason(Status status) {
      return Component.translatable("journal.colonyjournal.reason." + status.name().toLowerCase());
   }

   private void collectLinks() {
      for (BuildingGraph.Edge edge : BuildingGraph.EDGES) {
         Status producer = this.statusOf(edge.from());
         Status consumer = this.statusOf(edge.to());
         boolean consumerPresent = consumer != Status.ABSENT;
         boolean broken = consumerPresent && !producer.working();
         this.links.add(new Link(edge, broken, producer, consumer));
         if (broken) {
            Component text = Component.translatable(
               "journal.colonyjournal.issue.chain",
               nameOf(edge.to()),
               Component.translatable("journal.colonyjournal.need." + edge.need()),
               nameOf(edge.from()),
               reason(producer)
            );
            this.issues.add(new Issue(edge.optional() ? Severity.INFO : Severity.WARN, text, edge.from()));
         }
      }
   }

   private void collectBasics() {
      for (String id : BuildingGraph.BASICS) {
         Status status = this.statusOf(id);
         if (!status.working()) {
            Component text = Component.translatable("journal.colonyjournal.issue.basic", nameOf(id), reason(status));
            this.issues.add(new Issue(Severity.ERROR, text, id));
         }
      }

      if (this.citizenLimit > 0 && this.citizens >= this.citizenLimit) {
         this.issues.add(new Issue(Severity.WARN, Component.translatable("journal.colonyjournal.issue.housing", this.citizens, this.citizenLimit), "residence"));
      }
   }

   private void collectWorkerIssues() {
      for (Row row : this.rows) {
         if (row.status() == Status.NO_WORKER) {
            this.issues.add(new Issue(Severity.WARN, Component.translatable("journal.colonyjournal.issue.no_worker", row.name(), row.pos().toShortString()), row.typeId()));
         } else if (row.status() == Status.UNBUILT) {
            this.issues.add(new Issue(Severity.INFO, Component.translatable("journal.colonyjournal.issue.unbuilt", row.name(), row.pos().toShortString()), row.typeId()));
         }
      }
   }

   private void collectRequestIssues(Map<BlockPos, List<IRequest<?>>> requests, Map<String, List<IBuildingView>> byType) {
      Map<String, Integer> counts = new LinkedHashMap<>();
      Map<String, Issue> issues = new LinkedHashMap<>();

      for (Row row : this.rows) {
         for (IRequest<?> request : requests.getOrDefault(row.pos(), List.of())) {
            if (!open(request)) {
               continue;
            }

            List<String> producers = NeedClassifier.producers(request);
            String what = describe(request).getString();
            String key = row.typeId() + "|" + what;
            counts.merge(key, 1, Integer::sum);
            if (issues.containsKey(key)) {
               continue;
            }

            Component line;
            Severity severity;
            Status best = Status.ABSENT;
            String focus = row.typeId();
            for (String producer : producers) {
               Status status = this.statusOf(producer);
               if (status.ordinal() > best.ordinal()) {
                  best = status;
                  focus = producer;
               }
            }

            if (producers.isEmpty()) {
               line = Component.translatable("journal.colonyjournal.issue.request_manual", row.name(), what);
               severity = request.getState() == RequestState.REPORTED ? Severity.WARN : Severity.INFO;
            } else if (best.working()) {
               line = Component.translatable("journal.colonyjournal.issue.request_covered", row.name(), what, nameOf(focus));
               severity = request.getState() == RequestState.REPORTED ? Severity.WARN : Severity.INFO;
            } else {
               line = Component.translatable("journal.colonyjournal.issue.request_blocked", row.name(), what, nameOf(producers.get(0)), reason(best));
               severity = Severity.ERROR;
            }

            issues.put(key, new Issue(severity, line, focus));
         }
      }

      issues.forEach((key, issue) -> {
         int n = counts.get(key);
         this.issues.add(n > 1 ? new Issue(issue.severity(), Component.empty().append(issue.text()).append(" ×" + n), issue.focus()) : issue);
      });
   }

   private static Component describe(IRequest<?> request) {
      List<ItemStack> stacks = request.getDisplayStacks();
      if (stacks != null && !stacks.isEmpty() && !stacks.get(0).isEmpty()) {
         return stacks.get(0).getHoverName();
      }

      return request.getShortDisplayString();
   }
}
