package ru.vyarus.yaml.config.updater.parse.struct;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.*;
import ru.vyarus.yaml.config.updater.parse.struct.model.YamlStruct;
import ru.vyarus.yaml.config.updater.parse.struct.model.YamlStructTree;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vyacheslav Rusakov
 * @since 05.05.2021
 */
public class StructureReader {

    public static YamlStructTree read(final File file) {
        // comments parser does not support multiple yaml documents because this is not common for configs
        // so parsing only the first document, ignoring anything else
        try {
            final Node node = new Yaml().compose(new FileReader(file));
            final Context context = new Context();
            processNode(node, context);
            return new YamlStructTree(context.rootNodes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse yaml structure: " + file.getAbsolutePath(), e);
        }
    }

    private static void processNode(final Node node, final Context context) {
        if (node instanceof MappingNode) {
            for (NodeTuple tuple : ((MappingNode) node).getValue()) {
                if (!(tuple.getKeyNode() instanceof ScalarNode)) {
                    throw new IllegalStateException("Unsupported key node type " + tuple.getKeyNode()
                            + " in tuple " + tuple);
                }
                ScalarNode key = (ScalarNode) tuple.getKeyNode();
                String value = null;
                if (tuple.getValueNode() instanceof ScalarNode) {
                    value = ((ScalarNode) tuple.getValueNode()).getValue();
                }
                context.property(key.getStartMark().getColumn(), key.getStartMark().getLine() + 1, key.getValue(), value);

                // lists or sub objects
                if (!(tuple.getValueNode() instanceof ScalarNode)) {
                    processNode(tuple.getValueNode(), context);
                }
            }
        } else if (node instanceof SequenceNode) {
            // list value
            for (Node seq : ((SequenceNode) node).getValue()) {
                // need position of dash, which is absent here, so just assuming -2 shift from value
                final int listPad = seq.getStartMark().getColumn() - 2;

                if (seq instanceof ScalarNode) {
                    // simple value
                    context.listValue(listPad, seq.getStartMark().getLine() + 1, ((ScalarNode) seq).getValue());
                } else {
                    boolean tickSameLine = seq.getStartMark().get_snippet().trim().startsWith("-");
                    if (!tickSameLine) {
                        // case when properties start after empty dash (next line)
                        // and hierarchically it must be reproduced (unification with comments parser)
                        context.listValue(listPad, seq.getStartMark().getLine() + 1, null);
                    } else {
                        // sub object: first property of it must be list node and other properties would be sub-nodes
                        // (split and shift object)

                        context.listPad = listPad;
                    }

                    processNode(seq, context);
                }
            }
        } else {
            throw new IllegalStateException("Unsupported node type: " + node);
        }
    }

    private static class Context {
        List<YamlStruct> rootNodes = new ArrayList<>();
        YamlStruct current;
        // indicates list value before object item (to store first property as list value with dash padding)
        int listPad;

        public void property(final int padding, final int lineNum, final String name, final String value) {
            YamlStruct root = null;

            // for objects under list value, padding would be property start, not "tick" position
            int pad = listPad > 0 ? listPad: padding;
            // not true only for getting back from subtree to root level
            if (pad > 0 && current != null) {
                root = current;
                while (root != null && root.getPadding() >= pad) {
                    root = root.getRoot();
                }
            }
            // for lists, using dash padding on the first record instead of property position
            YamlStruct node = new YamlStruct(root, listPad > 0 ? listPad : padding, lineNum);
            if (listPad > 0) {
                node.setListValue(true);
                // reset list marker (in case of list object all subsequent properties must be children of the first one)
                listPad = 0;
            }
            if (name != null) {
                node.setKey(name);
            }
            if (value != null) {
                node.setValue(value);
                // different only for lists (first list item property)
                node.setKeyPadding(padding);
            }
            current = node;
            if (root == null) {
                rootNodes.add(node);
            }
        }

        public void listValue(final int padding, final int lineNum, final String value) {
            listPad = padding;
            property(padding, lineNum, null, value);
        }
    }
}
