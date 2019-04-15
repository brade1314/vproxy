package net.cassite.vproxy.component.svrgroup;

import net.cassite.vproxy.component.exception.AlreadyExistException;
import net.cassite.vproxy.component.exception.NotFoundException;
import net.cassite.vproxy.connection.Connector;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ServerGroups {
    public class ServerGroupHandle {
        public final String alias;
        public final ServerGroup group;
        private int weight;

        public ServerGroupHandle(ServerGroup group, int weight) {
            this.alias = group.alias;
            this.group = group;
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
            recalculateWRR();
        }
    }

    class WRR {
        final AtomicInteger cursor = new AtomicInteger(0);
        final ArrayList<ServerGroupHandle> groups;
        int[] seq;

        WRR(ArrayList<ServerGroupHandle> serverGroups) {
            this.groups = serverGroups;
        }
    }

    public final String alias;
    private ArrayList<ServerGroupHandle> serverGroups = new ArrayList<>(0);
    private WRR _wrr;

    public ServerGroups(String alias) {
        this.alias = alias;
        recalculateWRR();
    }

    private void recalculateWRR() {
        ArrayList<ServerGroupHandle> groups =
            serverGroups
                .stream()
                .filter(g -> g.weight > 0)
                .collect(Collectors.toCollection(ArrayList::new));
        WRR wrr = new WRR(groups);

        if (wrr.groups.isEmpty()) {
            wrr.seq = new int[0];
        } else {
            // calculate the seq
            List<Integer> listSeq = new LinkedList<>();
            int[] weights = new int[wrr.groups.size()];
            int[] original = new int[wrr.groups.size()];
            // run calculation
            int sum = 0;
            for (int i = 0; i < wrr.groups.size(); i++) {
                ServerGroupHandle h = wrr.groups.get(i);
                weights[i] = h.weight;
                original[i] = h.weight;
                sum += h.weight;
            }
            //noinspection Duplicates
            while (true) {
                int idx = maxIndex(weights);
                listSeq.add(idx);
                weights[idx] -= sum;
                if (calculationEnd(weights)) {
                    break;
                }
                for (int i = 0; i < weights.length; ++i) {
                    weights[i] += original[i];
                }
                sum = sum(weights); // recalculate sum
            }
            int[] seq = new int[listSeq.size()];
            Iterator<Integer> ite = listSeq.iterator();
            int seqIdx = 0;
            while (ite.hasNext()) {
                int idx = ite.next();
                seq[seqIdx++] = idx;
            }

            wrr.seq = seq;
        }

        _wrr = wrr;
    }

    private static int sum(int[] weights) {
        int s = 0;
        for (int w : weights) {
            s += w;
        }
        return s;
    }

    private static boolean calculationEnd(int[] weights) {
        for (int w : weights) {
            if (w != 0)
                return false;
        }
        return true;
    }

    private static int maxIndex(int[] weights) {
        int maxIdx = 0;
        int maxVal = weights[0];
        for (int i = 1; i < weights.length; ++i) {
            if (weights[i] > maxVal) {
                maxVal = weights[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    public void add(ServerGroup group, int weight) throws AlreadyExistException {
        List<ServerGroupHandle> groups = serverGroups;
        if (groups.stream().anyMatch(g -> g.group.equals(group)))
            throw new AlreadyExistException();
        ArrayList<ServerGroupHandle> newLs = new ArrayList<>(groups.size() + 1);
        newLs.addAll(groups);
        newLs.add(new ServerGroupHandle(group, weight));
        serverGroups = newLs;
        recalculateWRR();
    }

    public synchronized void remove(ServerGroup group) throws NotFoundException {
        List<ServerGroupHandle> groups = serverGroups;
        if (groups.isEmpty())
            throw new NotFoundException();
        boolean found = false;
        ArrayList<ServerGroupHandle> newLs = new ArrayList<>(groups.size() - 1);
        for (ServerGroupHandle g : groups) {
            if (g.group.equals(group)) {
                found = true;
            } else {
                newLs.add(g);
            }
        }
        if (!found) {
            throw new NotFoundException();
        }
        serverGroups = newLs;
        recalculateWRR();
    }

    public List<ServerGroupHandle> getServerGroups() {
        return new ArrayList<>(serverGroups);
    }

    public Connector next(InetSocketAddress source) {
        WRR wrr = _wrr;
        return next(source, wrr, 0);
    }

    private /*use static to prevent access local variable*/ static Connector next(InetSocketAddress source, WRR wrr, int recursion) {
        if (recursion > wrr.seq.length)
            return null;
        ++recursion;

        int idx = wrr.cursor.getAndIncrement();
        if (wrr.seq.length <= idx) {
            idx = idx % wrr.seq.length;
            wrr.cursor.set(idx + 1);
        }
        Connector connector = wrr.groups.get(wrr.seq[idx]).group.next(source);
        if (connector != null)
            return connector;
        return next(source, wrr, recursion);
    }
}
