package testing.dialogs;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.TextField.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import testing.*;
import testing.buttons.*;
import testing.ui.*;
import testing.util.*;

import static arc.Core.*;
import static mindustry.Vars.*;
import static testing.ui.TUDialogs.*;

public class UnitDialog extends TUBaseDialog{
    static final int multiplayerLimit = 10;

    TextField search;
    Table selection = new Table();
    UnitType spawnUnit = UnitTypes.dagger;
    Team spawnTeam = Team.get(settings.getInt("tu-default-team", 1));
    Vec2 spawnPos = new Vec2(Float.MIN_VALUE, Float.MIN_VALUE);
    int amount = 1;
    float radius = 2;
    static boolean despawns = true, initialized;

    boolean expectingPos;

    final int maxAmount = 100;
    final float minRadius = 0f, maxRadius = 10f;

    public UnitDialog(){
        super("@tu-unit-menu.name");

        shouldPause = false;
        addCloseButton();
        shown(this::rebuild);
        onResize(this::rebuild);
        despawns = settings.getBool("tu-despawns", true);

        cont.table(s -> {
            s.image(Icon.zoom).padRight(8);
            search = s.field(null, text -> rebuild()).growX().get();
            search.setMessageText("@players.search");
        }).fillX().padBottom(4).row();

        cont.label(() -> bundle.get("tu-menu.selection") + spawnUnit.localizedName).padBottom(6).row();

        cont.pane(all -> all.add(selection)).row();

        cont.table(s -> {
            Element[] elements = TUElements.sliderSet(
                s, text -> amount = Strings.parseInt(text), () -> String.valueOf(amount),
                TextFieldFilter.digitsOnly, Strings::canParsePositiveInt,
                1, maxAmount, 1, amount, (n, f) -> {
                    amount = n.intValue();
                    f.setText(String.valueOf(n));
                },
                "@tu-unit-menu.amount",
                "@tu-tooltip.unit-amount"
            );
            Slider sl = (Slider)elements[0];
            TextField tf = (TextField)elements[1];
            sl.update(() -> {
                if(net.client() && !sl.isDragging() && amount > multiplayerLimit){
                    amount = multiplayerLimit;
                    sl.setValue(amount);
                    tf.setText(amount + "");
                    ui.showInfoToast("@tu-unit-menu.limit", 4f);
                }
            });
            s.row();

            TUElements.sliderSet(
                s, text -> radius = Strings.parseFloat(text), () -> String.valueOf(radius),
                TextFieldFilter.floatsOnly, Strings::canParsePositiveFloat,
                minRadius, maxRadius, 0.1f, radius, (n, f) -> {
                    radius = n;
                    f.setText(String.valueOf(n));
                },
                "@tu-unit-menu.radius",
                "@tu-tooltip.unit-radius"
            );
        }).padTop(6).row();

        cont.table(t -> {
            TUElements.imageButton(
                t, TUIcons.get(Icon.defense), TUStyles.lefti, TUVars.buttonSize,
                () -> teamDialog.show(spawnTeam, team -> spawnTeam = team),
                () -> bundle.format("tu-unit-menu.set-team", "[#" + spawnTeam.color + "]" + teamName() + "[]"),
                "@tu-tooltip.unit-set-team"
            );


            TUElements.imageButton(
                t, TUIcons.get(Icon.map), TUStyles.toggleRighti, TUVars.buttonSize,
                () -> {
                    hide();
                    expectingPos = true;
                },
                () -> bundle.format("tu-unit-menu.pos", spawnPos.x / 8f, spawnPos.y / 8f),
                "@tu-tooltip.unit-pos"
            );
        }).padTop(6).row();

        cont.table(b -> {
            ImageButton ib = TUElements.imageButton(
                b, TUIcons.get(Icon.units), TUStyles.lefti, TUVars.buttonSize,
                this::transform,
                () -> "@tu-unit-menu.transform",
                "@tu-tooltip.unit-transform"
            );
            ib.setDisabled(() -> player.unit().type.internal);

            ImageButton db = TUElements.imageButton(
                b, TUIcons.alpha, TUStyles.toggleRighti, TUVars.buttonSize,
                () -> despawns = !despawns,
                () -> "@tu-unit-menu.despawns",
                "@tu-tooltip.unit-despawns"
            );
            db.update(() -> db.setChecked(despawns));
        }).padTop(6).row();

        cont.table(b -> {
            TUElements.imageButton(
                b, TUIcons.get(Icon.add), TUStyles.lefti, TUVars.buttonSize,
                this::spawn,
                () -> "@tu-unit-menu." + (amount != 1 ? "spawn-plural" : "spawn"),
                "@tu-tooltip.unit-spawn"
            );

            TUElements.imageButton(
                b, TUIcons.get(Icon.waves), TUStyles.toggleRighti, TUVars.buttonSize,
                () -> waveChangeDialog.show(),
                () -> "@tu-unit-menu.waves",
                "@tu-tooltip.unit-set-wave"
            );
        }).padTop(6);

        if(!initialized){
            Events.on(WorldLoadEndEvent.class, e -> {
                if(spawnPos.x == Float.MIN_VALUE){
                    spawnPos.set(world.unitWidth() / 2f, world.unitHeight() / 2f);
                }
            });

            Events.run(Trigger.update, () -> {
                if(expectingPos){
                    if(!state.isGame()){
                        expectingPos = false;
                    }else if(TestUtils.click()){
                        if(!scene.hasMouse()){
                            spawnPos.set(input.mouseWorld());
                            ui.showInfoToast(bundle.format("tu-unit-menu.set-pos", spawnPos.x / 8f, spawnPos.y / 8f), 4f);
                            show();
                        }else{
                            ui.showInfoToast("@tu-unit-menu.cancel", 4f);
                        }
                        expectingPos = false;
                    }
                }
            });
            initialized = true;
        }
    }

    public void drawPos(){
        float x, y;
        if(expectingPos && state.isGame() && !scene.hasMouse()){
            x = input.mouseWorldX();
            y = input.mouseWorldY();
        }else if(Spawn.spawnHover && !TestUtils.disableButton()){
            x = spawnPos.x;
            y = spawnPos.y;
        }else{
            return;
        }
        Draw.z(Layer.overlayUI);
        Lines.stroke(1f, spawnTeam.color);
        if(radius > 0.01f) Lines.circle(x, y, radius * tilesize);
        Draw.rect(Icon.cancel.getRegion(), x, y, tilesize / 2f, tilesize / 2f);
    }

    void rebuild(){
        expectingPos = false;
        selection.clear();
        String text = search.getText();

        Seq<UnitType> array = content.units()
            .select(e -> !e.internal &&
                (!e.isHidden() || settings.getBool("tu-show-hidden")) &&
                (text.isEmpty() || e.localizedName.toLowerCase().contains(text.toLowerCase())));
        if(array.size == 0) return;

        selection.table(list -> {
            list.left();

            float iconMul = 1.5f;
            int cols = (int)Mathf.clamp((graphics.getWidth() - Scl.scl(30)) / Scl.scl(32 + 10) / iconMul, 1, 22 / iconMul);
            int count = 0;

            for(UnitType u : array){
                Image image = new Image(u.uiIcon).setScaling(Scaling.fit);
                list.add(image).size(8 * 4 * iconMul).pad(3);

                ClickListener listener = new ClickListener();
                image.addListener(listener);
                if(!mobile){
                    image.addListener(new HandCursorListener());
                    image.update(() -> image.color.lerp(listener.isOver() || spawnUnit == u ? Color.white : Color.lightGray, Mathf.clamp(0.4f * Time.delta)));
                }else{
                    image.update(() -> image.color.lerp(spawnUnit == u ? Color.white : Color.lightGray, Mathf.clamp(0.4f * Time.delta)));
                }

                image.clicked(() -> {
                    if(input.keyDown(KeyCode.shiftLeft) && Fonts.getUnicode(u.name) != 0){
                        app.setClipboardText((char)Fonts.getUnicode(u.name) + "");
                        ui.showInfoFade("@copied");
                    }else{
                        spawnUnit = u;
                    }
                });
                TUElements.boxTooltip(image, u.localizedName);

                if((++count) % cols == 0){
                    list.row();
                }
            }
        }).growX().left().padBottom(10);
    }

    void spawn(){
        if(net.client()){
            Utils.runCommand("let setPos = () => Tmp.v1.setToRandomDirection().setLength(" + radius * tilesize + "*Mathf.sqrt(Mathf.random())).add(" + spawnPos.x + "," + spawnPos.y + ")");
            Utils.runCommand("for(let i=0;i<" + amount + ";i++){setPos();Vars.content.unit(" + spawnUnit.id + ").spawn(Team.get(" + spawnTeam.id + "),Tmp.v1.x,Tmp.v1.y);}");
        }else{
            for(int i = 0; i < amount; i++){
                float r = radius * tilesize * Mathf.sqrt(Mathf.random());
                Tmp.v1.setToRandomDirection().setLength(r).add(spawnPos);
                spawnUnit.spawn(spawnTeam, Tmp.v1);
            }
        }
    }

    void transform(){
        if(net.client()){
            Utils.runCommandPlayer("let s=Vars.content.unit(" + spawnUnit.id + ").spawn(e.team(), e);Call.unitControl(e,s);" + (despawns ? "e.unit().spawnedByCore=true;" : ""));
        }else if(player.unit() != null){
            Unit u = spawnUnit.spawn(player.team(), player);
            float rot = player.unit().rotation;
            u.controller(player);
            u.rotation(rot);
            u.spawnedByCore(despawns);
            Fx.unitControl.at(u, true);
        }
        hide();
    }
    String teamName(){
        return teamDialog.teamName(spawnTeam);
    }

    public UnitType getUnit(){
        return spawnUnit;
    }
}
