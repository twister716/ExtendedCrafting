<recipetype:extendedcrafting:combination>.addRecipe("test_combination", <item:minecraft:arrow>, 100000, <item:minecraft:carrot>, [<item:minecraft:potato>, <item:minecraft:potato>]);
<recipetype:extendedcrafting:combination>.addRecipe("test_combination_many_outputs", <item:minecraft:stick> * 10, 10000, <item:minecraft:diamond>, [<item:minecraft:diamond>, <tag:item:c:ingots/iron>, <item:minecraft:stick>]);

<recipetype:extendedcrafting:combination>.addRecipe("639dc8c0-9ddb-4501-8df5-c1e98311972c", <item:extendedcrafting:frame>, 100000, <item:extendedcrafting:compressor>, [
	<item:minecraft:hopper>, <item:minecraft:hopper>, <item:minecraft:hopper>, <item:minecraft:hopper>, <item:minecraft:hopper>
]);

var recipes = <recipetype:extendedcrafting:combination>.allRecipes;

println("There are " + recipes.length + " combination crafting recipes");